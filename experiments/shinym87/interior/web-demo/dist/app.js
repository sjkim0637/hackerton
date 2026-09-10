import * as THREE from "./vendor/three.module.min.js";
import { GLTFLoader } from "./vendor/GLTFLoader.js";

const products=[
  {id:"demo-soft-cloud-sofa",category:"소파",name:"소프트 클라우드 3인 소파",variant:"웜 아이보리 부클 · 데모 디자인",size:"220×92×84cm",price:"DEMO ONLY",type:"sofa",dims:[2.2,.84,.92],color:"#ded2bf",image:"demo-products/soft-cloud-sofa.webp",model:"models/demo-soft-cloud-sofa.gltf",anchor:[.5,.91]},
  {id:"demo-oak-stone-table",category:"테이블",name:"오크 스톤 커피테이블",variant:"라이트 오크·오프화이트 스톤 · 데모 디자인",size:"110×60×42cm",price:"DEMO ONLY",type:"table",dims:[1.1,.42,.6],color:"#c9a77f",image:"demo-products/oak-stone-table.webp",model:"models/demo-oak-stone-table.gltf",anchor:[.5,.88]},
  {id:"demo-oak-lounge-chair",category:"의자",name:"오크 커브 라운지체어",variant:"내추럴 오크·아이보리 패브릭 · 데모 디자인",size:"72×78×88cm",price:"DEMO ONLY",type:"chair",dims:[.72,.88,.78],color:"#d6b68e",image:"demo-products/oak-lounge-chair.webp",model:"models/demo-oak-lounge-chair.gltf",anchor:[.5,.94]},
  {id:"demo-white-oak-console",category:"수납",name:"화이트 오크 로우 콘솔",variant:"웜 화이트·라이트 오크 · 데모 디자인",size:"160×40×42cm",price:"DEMO ONLY",type:"shelf",dims:[1.6,.42,.4],color:"#e8dfd1",image:"demo-products/white-oak-console.webp",model:"models/demo-white-oak-console.gltf",anchor:[.5,.92]}
];

const $=selector=>document.querySelector(selector);
const room=$("#room"),overlay=$("#scene"),overlayContext=overlay.getContext("2d"),webgl=$("#webglScene");
const catalog=$("#catalog"),filters=$("#filters"),applyButton=$("#apply"),selectedInfo=$("#selectedInfo"),selectionSummary=$("#selectionSummary");
const emptyState=$("#empty"),toast=$("#toast"),roomPhoto=$("#roomPhoto"),fovControl=$("#fovControl"),floorControl=$("#floorControl");
const fovValue=$("#fovValue"),floorValue=$("#floorValue"),spaceMeta=$("#spaceMeta"),floorLine=$(".floor-line");
const calibrationOverlay=$("#calibrationOverlay"),floorPolygon=$("#floorPolygon"),vanishLeft=$("#vanishLeft"),vanishRight=$("#vanishRight");

const renderer=new THREE.WebGLRenderer({canvas:webgl,alpha:true,antialias:true,premultipliedAlpha:true});
renderer.setPixelRatio(Math.min(devicePixelRatio||1,2));
renderer.shadowMap.enabled=true;
renderer.shadowMap.type=THREE.PCFSoftShadowMap;
renderer.outputColorSpace=THREE.SRGBColorSpace;
renderer.toneMapping=THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure=1.05;
const scene=new THREE.Scene();
const camera3d=new THREE.PerspectiveCamera(62,1,.05,30);
const loader=new GLTFLoader();
const raycaster=new THREE.Raycaster();
const pointer=new THREE.Vector2();
const modelCache=new Map();
const textureCache=new Map();
const worldGroup=new THREE.Group();
scene.add(worldGroup);

scene.add(new THREE.HemisphereLight(0xdde9ff,0x8a7158,1.55));
const keyLight=new THREE.DirectionalLight(0xfff3da,3.2);
keyLight.position.set(-3.5,6,4.5); keyLight.castShadow=true;
keyLight.shadow.mapSize.set(1024,1024); keyLight.shadow.camera.left=-5; keyLight.shadow.camera.right=5;
keyLight.shadow.camera.top=5; keyLight.shadow.camera.bottom=-5; keyLight.shadow.bias=-.0004;
scene.add(keyLight);
const fillLight=new THREE.DirectionalLight(0xc8dcff,.8); fillLight.position.set(4,2,1); scene.add(fillLight);
const shadowPlane=new THREE.Mesh(new THREE.PlaneGeometry(10,7),new THREE.ShadowMaterial({color:0x18201c,opacity:.24}));
shadowPlane.rotation.x=-Math.PI/2; shadowPlane.position.y=-.008; shadowPlane.receiveShadow=true; scene.add(shadowPlane);

let chosenProduct=null,selectedItem=null,placedItems=[],activeFilter="전체",dragState=null,placementHidden=false;
let roomAspect=720/482,customRoomUrl=null,sceneWidth=0,sceneHeight=0,calibrationActive=false;
const cameraState={fov:62};
const SPACE_CONFIG={
  floorPolygon:[{x:.23,y:.59},{x:.76,y:.59},{x:.96,y:.98},{x:.04,y:.98}],
  vanishingPoint:{x:.5,y:.31},
  floorNearY:.98,
  floorFarY:.59,
  minPerspectiveScale:.45,
  maxPerspectiveScale:1.25
};
const calibration={
  backLeft:{...SPACE_CONFIG.floorPolygon[0]},backRight:{...SPACE_CONFIG.floorPolygon[1]},
  frontRight:{...SPACE_CONFIG.floorPolygon[2]},frontLeft:{...SPACE_CONFIG.floorPolygon[3]},
  vanish:{...SPACE_CONFIG.vanishingPoint}
};
const builtInItems=[
  {id:"sofa",isBuiltIn:true,name:"기존 소파",present:localStorage.getItem("roomfit-builtin-sofa")!=="false",area:{left:0,top:.43,right:.49,bottom:1},polygon:[[0,.48],[.23,.43],[.49,.61],[.49,.88],[.28,1],[0,1]],restoration:"restorations/sofa.png",zone:{x:-1.55,z:.55,w:2.2,d:1.25}},
  {id:"table",isBuiltIn:true,name:"기존 탁자",present:localStorage.getItem("roomfit-builtin-table")!=="false",area:{left:.36,top:.68,right:.66,bottom:.96},polygon:[[.39,.72],[.58,.69],[.66,.78],[.61,.96],[.39,.95]],restoration:"restorations/table.png",zone:{x:.05,z:.8,w:1.15,d:.7}},
  {id:"tv",isBuiltIn:true,name:"기존 벽걸이 TV",present:localStorage.getItem("roomfit-builtin-tv")!=="false",area:{left:.77,top:.13,right:1,bottom:.68},polygon:[[.79,.16],[1,.09],[1,.67],[.8,.59]],restoration:"restorations/tv.png",zone:null}
];
const roomImage=new Image(); roomImage.src="room-uploaded.png"; roomImage.onload=render;
const restorationImages={};
builtInItems.forEach(item=>{const image=new Image();image.src=item.restoration;image.onload=render;restorationImages[item.id]=image;});

try{const saved=JSON.parse(localStorage.getItem("roomfit-calibration")||"null");if(Number.isFinite(saved?.fov))cameraState.fov=saved.fov;for(const key of Object.keys(calibration))if(Number.isFinite(saved?.[key]?.x)&&Number.isFinite(saved?.[key]?.y))calibration[key]={x:saved[key].x,y:saved[key].y};}catch{}

function announce(message){toast.textContent=message;toast.classList.add("show");clearTimeout(announce.timer);announce.timer=setTimeout(()=>toast.classList.remove("show"),1900);}
function updateRoomBackground(){room.style.backgroundImage=`url("${customRoomUrl||"room-uploaded.png"}")`;}
function updateRoomSizing(){const h=Math.max(360,innerHeight-130);room.style.setProperty("--room-aspect",roomAspect);room.style.maxWidth=Math.round(h*roomAspect)+"px";}
function resize(){
  const bounds=room.getBoundingClientRect(); sceneWidth=bounds.width;sceneHeight=bounds.height;
  renderer.setSize(sceneWidth,sceneHeight,false);camera3d.aspect=sceneWidth/sceneHeight;camera3d.updateProjectionMatrix();
  const ratio=Math.min(devicePixelRatio||1,2),w=Math.round(sceneWidth*ratio),h=Math.round(sceneHeight*ratio);
  if(overlay.width!==w||overlay.height!==h){overlay.width=w;overlay.height=h;overlay.style.width=sceneWidth+"px";overlay.style.height=sceneHeight+"px";}
  overlayContext.setTransform(ratio,0,0,ratio,0,0);
}

function updateCamera(){
  camera3d.fov=cameraState.fov;
  const vp=calibration.vanish;
  camera3d.position.set((.5-vp.x)*7.5,2.55+(vp.y-.30)*4.5,5.8);
  camera3d.lookAt(0,.48,-.25);camera3d.updateProjectionMatrix();
  fovControl.value=Math.round(cameraState.fov);fovValue.textContent=Math.round(cameraState.fov)+"°";
  const floorPercent=Math.round(((calibration.backLeft.y+calibration.backRight.y)/2)*100);
  floorControl.value=Math.max(65,Math.min(88,floorPercent));floorValue.textContent=floorPercent+"%";floorLine.style.top=floorPercent+"%";
  localStorage.setItem("roomfit-calibration",JSON.stringify({fov:cameraState.fov,...calibration}));
  updateCalibrationOverlay();render();
}

function updateCalibrationOverlay(){
  calibrationOverlay.classList.toggle("active",calibrationActive);
  const pts=[calibration.backLeft,calibration.backRight,calibration.frontRight,calibration.frontLeft];
  floorPolygon.setAttribute("points",pts.map(p=>`${p.x*sceneWidth},${p.y*sceneHeight}`).join(" "));
  [[vanishLeft,calibration.backLeft],[vanishRight,calibration.backRight]].forEach(([line,end])=>{
    line.setAttribute("x1",calibration.vanish.x*sceneWidth);line.setAttribute("y1",calibration.vanish.y*sceneHeight);
    line.setAttribute("x2",end.x*sceneWidth);line.setAttribute("y2",end.y*sceneHeight);
  });
  calibrationOverlay.querySelectorAll(".cal-point").forEach(el=>{const p=calibration[el.dataset.point];el.style.left=p.x*100+"%";el.style.top=p.y*100+"%";});
}
function buildCalibrationHandles(){
  Object.keys(calibration).forEach(key=>{
    const handle=document.createElement("button");handle.type="button";handle.className="cal-point"+(key==="vanish"?" vanish":"");handle.dataset.point=key;handle.setAttribute("aria-label",key==="vanish"?"소실점":"바닥 모서리");
    calibrationOverlay.appendChild(handle);
  });
}
buildCalibrationHandles();

let calibrationDrag=null;
calibrationOverlay.addEventListener("pointerdown",event=>{const handle=event.target.closest(".cal-point");if(!handle)return;calibrationDrag=handle.dataset.point;handle.setPointerCapture(event.pointerId);event.preventDefault();});
calibrationOverlay.addEventListener("pointermove",event=>{if(!calibrationDrag)return;const bounds=room.getBoundingClientRect();let x=Math.max(.02,Math.min(.98,(event.clientX-bounds.left)/bounds.width)),y=Math.max(.08,Math.min(.98,(event.clientY-bounds.top)/bounds.height));if(calibrationDrag==="vanish"){y=Math.min(y,Math.min(calibration.backLeft.y,calibration.backRight.y)-.04);}else{const left=calibrationDrag.endsWith("Left"),back=calibrationDrag.startsWith("back");x=left?Math.min(x,.48):Math.max(x,.52);if(back)y=Math.min(y,Math.min(calibration.frontLeft.y,calibration.frontRight.y)-.04);else y=Math.max(y,Math.max(calibration.backLeft.y,calibration.backRight.y)+.04);}calibration[calibrationDrag]={x,y};updateCamera();});
calibrationOverlay.addEventListener("pointerup",event=>{if(!calibrationDrag)return;calibrationDrag=null;event.target.releasePointerCapture?.(event.pointerId);placedItems.forEach(updateSpatialState);saveState();updateCamera();announce("공간 원근 보정값을 저장했습니다.");});
calibrationOverlay.addEventListener("pointercancel",()=>calibrationDrag=null);

function renderFilters(){filters.innerHTML="";["전체","소파","테이블","의자","수납"].forEach(category=>{const button=document.createElement("button");button.type="button";button.className="filter"+(category===activeFilter?" active":"");button.textContent=category;button.onclick=()=>{activeFilter=category;renderFilters();renderCatalog();};filters.appendChild(button);});}
function renderCatalog(){catalog.innerHTML="";products.filter(p=>activeFilter==="전체"||p.category===activeFilter).forEach(product=>{const card=document.createElement("article");card.className="card"+(chosenProduct?.id===product.id?" active":"");card.innerHTML=`<button class="card-select" type="button"><span class="kind">${product.category}</span><span class="thumb"><img src="${product.image}" alt="${product.name} ${product.variant}"><span class="image-fallback">이미지 준비 중</span></span><strong>${product.name}</strong><small>${product.variant} · ${product.size}</small><small class="price">${product.price}</small></button>`;
    card.querySelector("button").onclick=()=>{chosenProduct=product;selectionSummary.classList.add("show");selectionSummary.innerHTML=`<b>${product.name}</b><br>${product.variant} · ${product.size} · 데모 전용 3D 모델`;applyButton.disabled=false;applyButton.textContent="우리 집에 적용";renderCatalog();};catalog.appendChild(card);});}

function connectedCutout(image){
  const c=document.createElement("canvas"),ratio=Math.min(1,700/Math.max(image.naturalWidth,image.naturalHeight));c.width=Math.round(image.naturalWidth*ratio);c.height=Math.round(image.naturalHeight*ratio);
  const ctx=c.getContext("2d",{willReadFrequently:true});ctx.drawImage(image,0,0,c.width,c.height);const pixels=ctx.getImageData(0,0,c.width,c.height),d=pixels.data,w=c.width,h=c.height,v=new Uint8Array(w*h),q=new Int32Array(w*h);let a=0,b=0;
  const bg=i=>{const o=i*4,r=d[o],g=d[o+1],bl=d[o+2],light=(r+g+bl)/3,chroma=Math.max(r,g,bl)-Math.min(r,g,bl);return d[o+3]<20||(light>226&&chroma<34);};
  const add=i=>{if(i<0||i>=w*h||v[i]||!bg(i))return;v[i]=1;q[b++]=i;};for(let x=0;x<w;x++){add(x);add((h-1)*w+x);}for(let y=0;y<h;y++){add(y*w);add(y*w+w-1);}while(a<b){const i=q[a++],x=i%w,y=Math.floor(i/w);d[i*4+3]=0;if(x)add(i-1);if(x<w-1)add(i+1);if(y)add(i-w);if(y<h-1)add(i+w);}ctx.putImageData(pixels,0,0);return c;
}
function addPhotoFace(group,product){
  if(textureCache.has(product.id)){attachPhoto(group,product,textureCache.get(product.id));return;}
  const image=new Image();image.onload=()=>{const texture=new THREE.CanvasTexture(connectedCutout(image));texture.colorSpace=THREE.SRGBColorSpace;textureCache.set(product.id,texture);attachPhoto(group,product,texture);render();};image.src=product.image;
}
function attachPhoto(group,product,texture){
  const [w,h,d]=product.dims,material=new THREE.MeshBasicMaterial({map:texture,transparent:true,alphaTest:.08,depthWrite:true,side:THREE.FrontSide,toneMapped:false});
  const face=new THREE.Mesh(new THREE.PlaneGeometry(w,h),material);face.name="actual-product-photo";
  face.position.set((.5-product.anchor[0])*w,(product.anchor[1]-.5)*h,d/2+.012);face.castShadow=true;group.add(face);
}
function loadModel(product){
  if(modelCache.has(product.id))return Promise.resolve(modelCache.get(product.id).clone(true));
  return new Promise(resolve=>loader.load(product.model,gltf=>{gltf.scene.traverse(node=>{if(node.isMesh){node.castShadow=true;node.receiveShadow=true;node.material=node.material.clone();}});modelCache.set(product.id,gltf.scene);resolve(gltf.scene.clone(true));},undefined,()=>resolve(fallbackModel(product))));
}
function fallbackModel(product){const [w,h,d]=product.dims,g=new THREE.Group(),m=new THREE.MeshStandardMaterial({color:product.color,roughness:.78});const mesh=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),m);mesh.position.y=h/2;mesh.castShadow=true;mesh.receiveShadow=true;g.add(mesh);return g;}

const lerp=(a,b,t)=>a+(b-a)*t;
const normalize=(value,min,max)=>Math.max(0,Math.min(1,(value-min)/(max-min||1)));
function horizontalBoundsAt(y){
  const intersections=[],polygon=[calibration.backLeft,calibration.backRight,calibration.frontRight,calibration.frontLeft];
  for(let i=0;i<polygon.length;i++){
    const a=polygon[i],b=polygon[(i+1)%polygon.length];
    if((y<a.y&&y<b.y)||(y>a.y&&y>b.y)||a.y===b.y)continue;
    const t=(y-a.y)/(b.y-a.y);if(t>=0&&t<=1)intersections.push(a.x+(b.x-a.x)*t);
  }
  intersections.sort((a,b)=>a-b);
  return intersections.length>=2?{left:intersections[0],right:intersections.at(-1)}:{left:.5,right:.5};
}
function clampToFloor(point){
  const floorFarY=(calibration.backLeft.y+calibration.backRight.y)/2,floorNearY=(calibration.frontLeft.y+calibration.frontRight.y)/2;
  const y=Math.max(floorFarY,Math.min(floorNearY,point.y));
  const bounds=horizontalBoundsAt(y);
  return {x:Math.max(bounds.left,Math.min(bounds.right,point.x)),y};
}
function perspectiveForY(y){
  const floorFarY=(calibration.backLeft.y+calibration.backRight.y)/2,floorNearY=(calibration.frontLeft.y+calibration.frontRight.y)/2;
  const depth=normalize(y,floorFarY,floorNearY);
  return lerp(SPACE_CONFIG.minPerspectiveScale,SPACE_CONFIG.maxPerspectiveScale,depth);
}
function screenAnchorToWorld(screenX,screenY){
  pointer.set(screenX*2-1,-(screenY*2-1));raycaster.setFromCamera(pointer,camera3d);
  const hit=new THREE.Vector3();
  return raycaster.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0,1,0),0),hit)||new THREE.Vector3(0,0,0);
}
function updateSpatialState(item){
  const clamped=clampToFloor({x:item.screenX,y:item.screenY});item.screenX=clamped.x;item.screenY=clamped.y;
  const ground=screenAnchorToWorld(item.screenX,item.screenY);item.x=ground.x;item.z=ground.z;
  item.perspectiveScale=perspectiveForY(item.screenY);item.finalScale=(item.manualScale??1)*item.perspectiveScale;
}
function updateModel(item){
  if(!item.object)return;updateSpatialState(item);item.object.position.set(item.x,0,item.z);item.object.rotation.set(0,item.rotation,0);
  item.object.scale.set((item.flipX?-1:1)*item.finalScale,item.finalScale,item.finalScale);item.object.visible=!placementHidden;
}
async function placeProduct(product,state={}){
  const floorFarY=(calibration.backLeft.y+calibration.backRight.y)/2,floorNearY=(calibration.frontLeft.y+calibration.frontRight.y)/2;
  const migratedY=Number.isFinite(state.screenY)?state.screenY:Math.max(floorFarY,Math.min(floorNearY,.76+(state.z??0)*.08));
  const item={product,screenX:state.screenX??.5,screenY:migratedY,manualScale:state.manualScale??1,flipX:Boolean(state.flipX),rotation:state.rotation??0,object:null,x:0,z:0};
  updateSpatialState(item);const open=findOpenPosition(item);if(!state.id&&open)Object.assign(item,open);updateSpatialState(item);if(isInvalidPlacement(item)){if(!open){showPlacementBlocked();return;}Object.assign(item,open);updateSpatialState(item);}
  item.object=await loadModel(product);item.object.userData.item=item;item.object.traverse(n=>n.userData.item=item);addPhotoFace(item.object,product);worldGroup.add(item.object);placedItems.push(item);updateModel(item);selectItem(item);saveState();emptyState.hidden=true;render();
}

function footprint(item,override={}){const x=override.x??item.x,z=override.z??item.z,r=override.rotation??item.rotation,manual=override.manualScale??item.manualScale??1,[w,,d]=item.product.dims,c=Math.cos(r),s=Math.sin(r);return [[-w*manual/2-.05,-d*manual/2-.05],[w*manual/2+.05,-d*manual/2-.05],[w*manual/2+.05,d*manual/2+.05],[-w*manual/2-.05,d*manual/2+.05]].map(([lx,lz])=>({x:x+lx*c-lz*s,z:z+lx*s+lz*c}));}
function polygonsOverlap(first,second){const axes=[];[first,second].forEach(p=>{for(let i=0;i<2;i++){const e={x:p[i+1].x-p[i].x,z:p[i+1].z-p[i].z},l=Math.hypot(e.x,e.z)||1;axes.push({x:-e.z/l,z:e.x/l});}});return axes.every(a=>{const f=first.map(p=>p.x*a.x+p.z*a.z),s=second.map(p=>p.x*a.x+p.z*a.z);return Math.max(...f)>Math.min(...s)&&Math.max(...s)>Math.min(...f);});}
function zonePolygon(zone){return [{x:zone.x-zone.w/2,z:zone.z-zone.d/2},{x:zone.x+zone.w/2,z:zone.z-zone.d/2},{x:zone.x+zone.w/2,z:zone.z+zone.d/2},{x:zone.x-zone.w/2,z:zone.z+zone.d/2}];}
function isInvalidPlacement(item,override={}){const p=footprint(item,override),outside=p.some(v=>v.x< -2.45||v.x>2.45||v.z< -1.55||v.z>1.35);return outside||placedItems.some(o=>o!==item&&polygonsOverlap(p,footprint(o)))||(!customRoomUrl&&builtInItems.some(b=>b.present&&b.zone&&polygonsOverlap(p,zonePolygon(b.zone))));}
function findOpenPosition(item){const candidates=[],far=(calibration.backLeft.y+calibration.backRight.y)/2+.04,near=(calibration.frontLeft.y+calibration.frontRight.y)/2-.04,step=Math.max(.035,(near-far)/6);for(let y=far;y<=near;y+=step){const bounds=horizontalBoundsAt(y);for(let x=bounds.left+.04;x<=bounds.right-.04;x+=.055)candidates.push({screenX:x,screenY:y});}candidates.sort((a,b)=>Math.abs(a.screenX-.5)+Math.abs(a.screenY-(far+near)/2)-Math.abs(b.screenX-.5)-Math.abs(b.screenY-(far+near)/2));return candidates.find(candidate=>{const before={screenX:item.screenX,screenY:item.screenY,x:item.x,z:item.z};Object.assign(item,candidate);updateSpatialState(item);const valid=!isInvalidPlacement(item);Object.assign(item,before);return valid;});}
function showPlacementBlocked(){announce("상품을 놓을 수 없습니다. 다른 가구와 겹치는 위치입니다.");}

function screenToFloor(clientX,clientY){const bounds=room.getBoundingClientRect();return clampToFloor({x:(clientX-bounds.left)/bounds.width,y:(clientY-bounds.top)/bounds.height});}
function selectItem(item){selectedItem=item||null;updateSelectedInfo();render();}
function updateSelectedInfo(){if(!selectedItem){selectedInfo.textContent="배치된 가구를 눌러 선택하세요.";return;}if(selectedItem.isBuiltIn){selectedInfo.innerHTML=`<b>${selectedItem.name}</b><br>사진 속 전경 가림 레이어 · 삭제하면 복원 이미지로 채웁니다.`;return;}const angle=((Math.round(selectedItem.rotation*180/Math.PI)%360)+360)%360;selectedInfo.innerHTML=`<b>${selectedItem.product.name}</b><br>${selectedItem.product.variant} · 실제 규격 ${selectedItem.product.size}<br>회전 ${angle}° · ${selectedItem.flipX?"좌우 반전 · ":""}수동 ${(selectedItem.manualScale*100).toFixed(0)}% · 원근 ${(selectedItem.perspectiveScale*100).toFixed(0)}%`;}
function saveState(){localStorage.setItem("roomfit-real-placement-v2",JSON.stringify(placedItems.map(i=>({id:i.product.id,screenX:i.screenX,screenY:i.screenY,manualScale:i.manualScale,flipX:i.flipX,rotation:i.rotation}))));}
function removeSelected(){if(!selectedItem)return;if(selectedItem.isBuiltIn){selectedItem.present=false;localStorage.setItem(`roomfit-builtin-${selectedItem.id}`,"false");}else{worldGroup.remove(selectedItem.object);placedItems=placedItems.filter(i=>i!==selectedItem);}selectedItem=null;updateSelectedInfo();saveState();render();announce("선택한 가구를 공간에서 제거했습니다.");}

function drawOverlay(){
  overlayContext.clearRect(0,0,sceneWidth,sceneHeight);
  builtInItems.forEach(item=>{
    item.bounds={left:item.area.left*sceneWidth,top:item.area.top*sceneHeight,right:item.area.right*sceneWidth,bottom:item.area.bottom*sceneHeight};
    if(customRoomUrl)return;
    if(!item.present){const restoration=restorationImages[item.id];if(restoration?.complete)overlayContext.drawImage(restoration,0,0,sceneWidth,sceneHeight);return;}
    if(roomImage.complete){overlayContext.save();overlayContext.beginPath();item.polygon.forEach(([x,y],index)=>index?overlayContext.lineTo(x*sceneWidth,y*sceneHeight):overlayContext.moveTo(x*sceneWidth,y*sceneHeight));overlayContext.closePath();overlayContext.clip();overlayContext.drawImage(roomImage,0,0,sceneWidth,sceneHeight);overlayContext.restore();}
    if(selectedItem===item){overlayContext.save();overlayContext.strokeStyle="#d7ff76";overlayContext.lineWidth=3;overlayContext.setLineDash([7,5]);overlayContext.strokeRect(item.bounds.left,item.bounds.top,item.bounds.right-item.bounds.left,item.bounds.bottom-item.bounds.top);overlayContext.restore();}
  });
  if(selectedItem&&!selectedItem.isBuiltIn&&selectedItem.object){const box=new THREE.Box3().setFromObject(selectedItem.object),corners=[];for(const x of [box.min.x,box.max.x])for(const y of [box.min.y,box.max.y])for(const z of [box.min.z,box.max.z]){const p=new THREE.Vector3(x,y,z).project(camera3d);corners.push({x:(p.x+1)*sceneWidth/2,y:(1-p.y)*sceneHeight/2});}const left=Math.min(...corners.map(p=>p.x)),right=Math.max(...corners.map(p=>p.x)),top=Math.min(...corners.map(p=>p.y)),bottom=Math.max(...corners.map(p=>p.y));selectedItem.bounds={left,top,right,bottom};overlayContext.strokeStyle="#d7ff76";overlayContext.lineWidth=3;overlayContext.setLineDash([7,5]);overlayContext.strokeRect(left-5,top-5,right-left+10,bottom-top+10);overlayContext.setLineDash([]);}
}
function render(){resize();updateCalibrationOverlay();worldGroup.visible=!placementHidden;updateCameraObjects();renderer.render(scene,camera3d);drawOverlay();}
function updateCameraObjects(){placedItems.forEach(updateModel);}

overlay.onpointerdown=event=>{
  if(calibrationActive)return;const bounds=room.getBoundingClientRect(),x=event.clientX-bounds.left,y=event.clientY-bounds.top;
  const built=[...builtInItems].reverse().find(i=>i.present&&x>=i.area.left*sceneWidth&&x<=i.area.right*sceneWidth&&y>=i.area.top*sceneHeight&&y<=i.area.bottom*sceneHeight);
  if(built){selectItem(built);announce(`${built.name}을 선택했습니다.`);return;}
  pointer.x=x/sceneWidth*2-1;pointer.y=-(y/sceneHeight)*2+1;raycaster.setFromCamera(pointer,camera3d);const hits=raycaster.intersectObjects(worldGroup.children,true);const item=hits.find(h=>h.object.userData.item)?.object.userData.item||null;selectItem(item);if(item){overlay.setPointerCapture(event.pointerId);dragState={item};}
};
overlay.onpointermove=event=>{if(!dragState?.item)return;const item=dragState.item,before={screenX:item.screenX,screenY:item.screenY,x:item.x,z:item.z},next=screenToFloor(event.clientX,event.clientY);item.screenX=next.x;item.screenY=next.y;updateSpatialState(item);if(isInvalidPlacement(item)){Object.assign(item,before);showPlacementBlocked();return;}updateModel(item);updateSelectedInfo();render();};
overlay.onpointerup=()=>{if(dragState?.item){saveState();announce("바닥 평면에 고정해 이동했습니다.");}dragState=null;};overlay.onpointercancel=()=>dragState=null;

function rotateSelected(delta){if(!selectedItem||selectedItem.isBuiltIn)return;const before=selectedItem.rotation;selectedItem.rotation+=delta;if(isInvalidPlacement(selectedItem)){selectedItem.rotation=before;showPlacementBlocked();}updateModel(selectedItem);updateSelectedInfo();saveState();render();}
function moveSelected(dx,dy){if(!selectedItem||selectedItem.isBuiltIn)return;const before={screenX:selectedItem.screenX,screenY:selectedItem.screenY,x:selectedItem.x,z:selectedItem.z},next=clampToFloor({x:selectedItem.screenX+dx,y:selectedItem.screenY+dy});Object.assign(selectedItem,{screenX:next.x,screenY:next.y});updateSpatialState(selectedItem);if(isInvalidPlacement(selectedItem)){Object.assign(selectedItem,before);showPlacementBlocked();return;}updateModel(selectedItem);updateSelectedInfo();saveState();render();}
function scaleSelected(factor){if(!selectedItem||selectedItem.isBuiltIn)return;const before=selectedItem.manualScale;selectedItem.manualScale=Math.max(.65,Math.min(1.4,before*factor));updateSpatialState(selectedItem);if(isInvalidPlacement(selectedItem)){selectedItem.manualScale=before;updateSpatialState(selectedItem);showPlacementBlocked();return;}updateModel(selectedItem);updateSelectedInfo();saveState();render();}
function flipSelected(){if(!selectedItem||selectedItem.isBuiltIn)return;selectedItem.flipX=!selectedItem.flipX;updateModel(selectedItem);updateSelectedInfo();saveState();render();announce(selectedItem.flipX?"가구를 좌우 반전했습니다.":"가구 반전을 해제했습니다.");}
overlay.onkeydown=event=>{let handled=true;if(event.key==="ArrowLeft")moveSelected(-.012,0);else if(event.key==="ArrowRight")moveSelected(.012,0);else if(event.key==="ArrowUp")moveSelected(0,-.012);else if(event.key==="ArrowDown")moveSelected(0,.012);else if(event.key.toLowerCase()==="q")rotateSelected(-Math.PI/12);else if(event.key.toLowerCase()==="e")rotateSelected(Math.PI/12);else if(event.key.toLowerCase()==="h")flipSelected();else if(event.key==="+"||event.key==="=")scaleSelected(1.05);else if(event.key==="-"||event.key==="_")scaleSelected(1/1.05);else if(event.key==="Delete"||event.key==="Backspace")removeSelected();else handled=false;if(handled)event.preventDefault();};

applyButton.onclick=()=>chosenProduct&&placeProduct(chosenProduct);
$(".controls").onclick=event=>{const action=event.target.dataset.action;if(!action)return;if(action==="calibrate"){toggleCalibration();return;}if(!selectedItem){announce("먼저 가구를 선택하세요.");return;}if(action==="remove"){removeSelected();return;}if(selectedItem.isBuiltIn){announce("사진 속 가구는 삭제만 가능합니다.");return;}if(action==="left")rotateSelected(-Math.PI/12);if(action==="right")rotateSelected(Math.PI/12);if(action==="back")moveSelected(0,-.035);if(action==="front")moveSelected(0,.035);if(action==="smaller")scaleSelected(1/1.05);if(action==="larger")scaleSelected(1.05);if(action==="flip")flipSelected();if(action==="auto-orient"){selectedItem.rotation=Math.round(selectedItem.rotation/(Math.PI/2))*Math.PI/2;updateModel(selectedItem);saveState();render();announce("가까운 벽 방향에 맞췄습니다.");}};
function toggleCalibration(){calibrationActive=!calibrationActive;updateCalibrationOverlay();announce(calibrationActive?"이 공간에 고정된 바닥 영역과 소실점입니다.":"바닥 영역 표시를 닫았습니다.");}
$("#autoCalibrate").onclick=toggleCalibration;
fovControl.oninput=()=>{cameraState.fov=Number(fovControl.value);updateCamera();};
floorControl.oninput=()=>{const target=Number(floorControl.value)/100,delta=target-(calibration.backLeft.y+calibration.backRight.y)/2;calibration.backLeft.y=Math.max(.35,Math.min(.84,calibration.backLeft.y+delta));calibration.backRight.y=Math.max(.35,Math.min(.84,calibration.backRight.y+delta));updateCamera();};
$("#togglePlacement").onclick=event=>{placementHidden=!placementHidden;event.target.textContent=placementHidden?"배치 보기":"배치 숨기기";render();};
$("#reset").onclick=()=>{placedItems.forEach(i=>worldGroup.remove(i.object));placedItems=[];selectedItem=null;chosenProduct=null;builtInItems.forEach(i=>{i.present=true;localStorage.setItem(`roomfit-builtin-${i.id}`,"true");});localStorage.removeItem("roomfit-real-placement-v2");selectionSummary.classList.remove("show");applyButton.disabled=true;applyButton.textContent="제품을 먼저 선택하세요";renderCatalog();updateSelectedInfo();render();announce("배치 상태를 초기화했습니다.");};
roomPhoto.onchange=event=>{const file=event.target.files?.[0];if(!file?.type.startsWith("image/")){announce("이미지 파일을 선택해 주세요.");return;}const url=URL.createObjectURL(file),image=new Image();image.onload=()=>{if(customRoomUrl)URL.revokeObjectURL(customRoomUrl);customRoomUrl=url;roomAspect=Math.max(.75,Math.min(2.4,image.naturalWidth/image.naturalHeight));placedItems.forEach(i=>worldGroup.remove(i.object));placedItems=[];selectedItem=null;updateRoomSizing();updateRoomBackground();spaceMeta.textContent=`사용자 사진 · ${image.naturalWidth}×${image.naturalHeight} · 5점 보정 필요`;calibrationActive=true;updateCamera();announce("사진을 불러왔습니다. 바닥 5점을 맞춰 주세요.");};image.src=url;};
window.addEventListener("resize",()=>{updateRoomSizing();updateCalibrationOverlay();render();});

updateRoomSizing();updateRoomBackground();renderFilters();renderCatalog();updateCamera();spaceMeta.textContent="업로드 거실 · 720×482 · 5점 원근·깊이 가림 적용";
try{const saved=JSON.parse(localStorage.getItem("roomfit-real-placement-v2")||"[]");for(const state of saved){const product=products.find(p=>p.id===state.id);if(product)await placeProduct(product,state);}}catch{localStorage.removeItem("roomfit-real-placement-v2");}
emptyState.hidden=placedItems.length>0||builtInItems.some(i=>i.present);render();
