// Three.js scene for the Roblox avatar viewer.
//
// The model URLs and camera data come from the Android JavaScript bridge
// (jsInterface). Nothing here fetches from Roblox directly, and there are
// no hard-coded fallback models: if the bridge has no avatar, loading
// reports an error through the bridge instead of rendering someone else's
// avatar.
//
// Three.js r182, vendored from the three npm package (see THREE-LICENSE.txt
// in this folder). Loaders are the package's examples/jsm addons, imported
// through the import map in index.html.
import * as THREE from "three";
import { MTLLoader } from "three/addons/loaders/MTLLoader.js";
import { OBJLoader } from "three/addons/loaders/OBJLoader.js";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";

let scene = null;
let canvas = null;
let camera = null;
let renderer = null;
let controls = null;
let modelRoot = null;
let pendingBackgroundColor = null;
let loadToken = 0;

function hasBridge() {
  return typeof jsInterface !== "undefined" && jsInterface !== null;
}

function reportError(message) {
  console.error(message);
  if (hasBridge() && typeof jsInterface.onLoadError === "function") {
    jsInterface.onLoadError(String(message));
  }
}

function isFiniteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function createScene() {
  scene = new THREE.Scene();
  canvas = document.querySelector("#c");
  renderer = new THREE.WebGLRenderer({ antialias: true, canvas });
  if (pendingBackgroundColor !== null) {
    scene.background = new THREE.Color(pendingBackgroundColor);
  }
  createLights();
}

function createLights() {
  const hemisphereLight = new THREE.HemisphereLight(0xb1e1ff, 0xb97a20, 3);
  scene.add(hemisphereLight);

  const directionalLight = new THREE.DirectionalLight(0xffffff, 3);
  directionalLight.position.set(5, 10, 2);
  scene.add(directionalLight);
  scene.add(directionalLight.target);
}

function disposeModel() {
  if (modelRoot === null) return;
  scene.remove(modelRoot);
  modelRoot.traverse((node) => {
    if (node.isMesh) {
      if (node.geometry) node.geometry.dispose();
      const materials = Array.isArray(node.material)
        ? node.material
        : [node.material];
      materials.forEach((material) => {
        if (!material) return;
        if (material.map) material.map.dispose();
        material.dispose();
      });
    }
  });
  modelRoot = null;
}

function frameArea(sizeToFitOnScreen, boxSize, boxCenter) {
  const halfSizeToFitOnScreen = sizeToFitOnScreen * 0.5;
  const halfFovY = THREE.MathUtils.degToRad(camera.fov * 0.5);
  const distance = halfSizeToFitOnScreen / Math.tan(halfFovY);
  const direction = new THREE.Vector3()
    .subVectors(camera.position, boxCenter)
    .multiply(new THREE.Vector3(1, 0, 1));
  if (direction.lengthSq() === 0) direction.set(0, 0, 1);
  direction.normalize();
  camera.position.copy(direction.multiplyScalar(distance).add(boxCenter));
  camera.near = boxSize / 100;
  camera.far = boxSize * 100;
  camera.updateProjectionMatrix();
  camera.lookAt(boxCenter);
  controls.target.copy(boxCenter);
  controls.update();
}

function resizeRendererToDisplaySize() {
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  const needResize = canvas.width !== width || canvas.height !== height;
  if (needResize) {
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
  }
  return needResize;
}

function startRenderingLoop() {
  function render() {
    requestAnimationFrame(render);
    if (renderer === null || camera === null) return;
    resizeRendererToDisplaySize();
    renderer.render(scene, camera);
  }
  requestAnimationFrame(render);
}

async function loadModel() {
  const token = ++loadToken;
  const mtlUrl = hasBridge() ? jsInterface.getMtlUrl() : "";
  const objUrl = hasBridge() ? jsInterface.getObjUrl() : "";
  if (!mtlUrl || !objUrl) {
    reportError("No avatar loaded yet.");
    return;
  }

  const fov = hasBridge() ? jsInterface.getFov() : 28;
  const cameraPosition = {
    x: hasBridge() ? jsInterface.getAvatarCameraPositionX() : 0,
    y: hasBridge() ? jsInterface.getAvatarCameraPositionY() : 10,
    z: hasBridge() ? jsInterface.getAvatarCameraPositionZ() : -20,
  };
  const aabbMin = {
    x: hasBridge() ? jsInterface.getAabbMinX() : 0,
    y: hasBridge() ? jsInterface.getAabbMinY() : 0,
    z: hasBridge() ? jsInterface.getAabbMinZ() : 0,
  };
  const aabbMax = {
    x: hasBridge() ? jsInterface.getAabbMaxX() : 0,
    y: hasBridge() ? jsInterface.getAabbMaxY() : 0,
    z: hasBridge() ? jsInterface.getAabbMaxZ() : 0,
  };
  const aabbUsable =
    isFiniteNumber(aabbMin.x) && isFiniteNumber(aabbMin.y) &&
    isFiniteNumber(aabbMin.z) && isFiniteNumber(aabbMax.x) &&
    isFiniteNumber(aabbMax.y) && isFiniteNumber(aabbMax.z) &&
    aabbMin.x <= aabbMax.x && aabbMin.y <= aabbMax.y && aabbMin.z <= aabbMax.z;

  camera = new THREE.PerspectiveCamera(
    isFiniteNumber(fov) && fov > 0 ? fov : 28,
    window.innerWidth / window.innerHeight,
    0.1,
    100,
  );
  camera.position.set(
    isFiniteNumber(cameraPosition.x) ? cameraPosition.x : 0,
    isFiniteNumber(cameraPosition.y) ? cameraPosition.y : 10,
    isFiniteNumber(cameraPosition.z) ? cameraPosition.z : -20,
  );
  controls = new OrbitControls(camera, canvas);
  controls.enablePan = false;
  controls.target.set(0, 5, 0);
  controls.update();

  try {
    const mtlLoader = new MTLLoader();
    const materials = await mtlLoader.loadAsync(mtlUrl);
    if (token !== loadToken) return; // A newer load superseded this one.
    materials.preload();

    const objLoader = new OBJLoader();
    objLoader.setMaterials(materials);
    const root = await objLoader.loadAsync(objUrl);
    if (token !== loadToken) return;

    const materialTextures = {};
    for (const name in materials.materialsInfo) {
      const materialInfo = materials.materialsInfo[name];
      if (materialInfo.map_ka && hasBridge()) {
        materialTextures[name] = jsInterface.getCDN(materialInfo.map_ka);
      }
    }
    root.traverse((node) => {
      if (node.isMesh) {
        node.material.opacity = 1;
        node.material.transparent = false;
        const textureUrl = materialTextures[node.material.name];
        if (textureUrl) {
          node.material.map = new THREE.TextureLoader().load(textureUrl);
        }
      }
    });

    disposeModel();
    modelRoot = root;
    scene.add(root);

    let box;
    if (aabbUsable) {
      box = new THREE.Box3(
        new THREE.Vector3(aabbMin.x, aabbMin.y, aabbMin.z),
        new THREE.Vector3(aabbMax.x, aabbMax.y, aabbMax.z),
      );
    } else {
      box = new THREE.Box3().setFromObject(root);
    }
    const boxSize = box.getSize(new THREE.Vector3()).length();
    const boxCenter = box.getCenter(new THREE.Vector3());
    if (boxSize > 0) {
      frameArea(boxSize * 1.2, boxSize, boxCenter);
      controls.maxDistance = boxSize * 10;
      controls.update();
    }
    if (hasBridge() && typeof jsInterface.onModelLoaded === "function") {
      jsInterface.onModelLoaded();
    }
  } catch (error) {
    if (token !== loadToken) return;
    reportError("Could not load the avatar model. Please try again.");
  }
}

window.loadAvatar = function () {
  loadModel();
};

window.changeBackgroundColor = function (color) {
  if (typeof color !== "string" || !/^#[0-9A-Fa-f]{6}$/.test(color)) return;
  pendingBackgroundColor = color;
  if (scene !== null) {
    scene.background = new THREE.Color(color);
  }
};

window.captureImage = function () {
  if (renderer === null || scene === null || camera === null || modelRoot === null) {
    reportError("No avatar is showing to capture yet.");
    return;
  }
  requestAnimationFrame(() => {
    // Render synchronously in this frame so toDataURL reads the frame
    // that was just drawn (preserveDrawingBuffer is off).
    renderer.render(scene, camera);
    const imageData = renderer.domElement.toDataURL("image/png");
    if (hasBridge()) {
      jsInterface.setImageData(imageData);
    }
  });
};

// The scene and its single render loop are created once. Each search only
// replaces the model; the original code called main() again on every
// submit and leaked a second loop.
createScene();
startRenderingLoop();
