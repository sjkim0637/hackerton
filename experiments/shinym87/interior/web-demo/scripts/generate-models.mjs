import fs from "node:fs";
import path from "node:path";

const output=path.resolve("dist/models");
fs.mkdirSync(output,{recursive:true});

const positions=[],normals=[],indices=[];
const faces=[
  {n:[0,0,1],v:[[-.5,-.5,.5],[.5,-.5,.5],[.5,.5,.5],[-.5,.5,.5]]},
  {n:[0,0,-1],v:[[.5,-.5,-.5],[-.5,-.5,-.5],[-.5,.5,-.5],[.5,.5,-.5]]},
  {n:[1,0,0],v:[[.5,-.5,.5],[.5,-.5,-.5],[.5,.5,-.5],[.5,.5,.5]]},
  {n:[-1,0,0],v:[[-.5,-.5,-.5],[-.5,-.5,.5],[-.5,.5,.5],[-.5,.5,-.5]]},
  {n:[0,1,0],v:[[-.5,.5,.5],[.5,.5,.5],[.5,.5,-.5],[-.5,.5,-.5]]},
  {n:[0,-1,0],v:[[-.5,-.5,-.5],[.5,-.5,-.5],[.5,-.5,.5],[-.5,-.5,.5]]}
];
faces.forEach((face,faceIndex)=>{
  face.v.forEach(v=>{positions.push(...v);normals.push(...face.n);});
  const base=faceIndex*4; indices.push(base,base+1,base+2,base,base+2,base+3);
});
const posBuffer=Buffer.from(new Float32Array(positions).buffer);
const normalBuffer=Buffer.from(new Float32Array(normals).buffer);
const indexBuffer=Buffer.from(new Uint16Array(indices).buffer);
const combined=Buffer.concat([posBuffer,normalBuffer,indexBuffer]);
const cubeBuffer=`data:application/octet-stream;base64,${combined.toString("base64")}`;

const rgb=hex=>{
  const n=parseInt(hex.slice(1),16);
  return [((n>>16)&255)/255,((n>>8)&255)/255,(n&255)/255,1];
};
const box=(name,x,y,z,w,h,d,material=0)=>({name,mesh:0,translation:[x,y,z],scale:[w,h,d],extras:{material}});

function parts(type,[w,h,d]){
  if(type==="sofa") return [
    box("seat",0,.31,0,w*.82,.30,d*.78),box("back",0,h*.68,-d*.37,w*.86,h*.62,d*.16),
    box("left-arm",-w*.46,.46,0,w*.09,.55,d*.9),box("right-arm",w*.46,.46,0,w*.09,.55,d*.9),
    box("left-cushion",-w*.22,.54,.02,w*.39,.18,d*.66,1),box("right-cushion",w*.22,.54,.02,w*.39,.18,d*.66,1),
    box("left-back",-w*.22,h*.69,-d*.25,w*.39,h*.48,d*.18,1),box("right-back",w*.22,h*.69,-d*.25,w*.39,h*.48,d*.18,1)
  ];
  if(type==="table") return [
    box("top",0,h-.055,0,w,.11,d),
    ...[-1,1].flatMap(sx=>[-1,1].map(sz=>box("leg",sx*w*.42,h*.46,sz*d*.38,.065,h*.9,.065)))
  ];
  if(type==="chair") return [
    box("seat",0,h*.46,0,w*.72,.13,d*.62,1),box("back",0,h*.73,-d*.30,w*.7,h*.53,.10,1),
    box("left-frame",-w*.4,h*.38,0,.055,h*.76,.07),box("right-frame",w*.4,h*.38,0,.055,h*.76,.07),
    box("left-base",-w*.4,.05,.06,.07,.10,d*.88),box("right-base",w*.4,.05,.06,.07,.10,d*.88)
  ];
  return [
    box("back",0,h/2,-d*.45,w,h,d*.10,1),box("left-side",-w*.46,h/2,0,w*.08,h,d),box("right-side",w*.46,h/2,0,w*.08,h,d),
    box("top",0,h-.04,0,w,.08,d),box("bottom",0,.04,0,w,.08,d),
    box("shelf-1",0,h*.25,0,w*.88,.055,d*.94),box("shelf-2",0,h*.50,0,w*.88,.055,d*.94),box("shelf-3",0,h*.75,0,w*.88,.055,d*.94)
  ];
}

const models=[
  ["demo-soft-cloud-sofa","sofa",[2.2,.84,.92],"#ded2bf"],
  ["demo-oak-stone-table","table",[1.1,.42,.6],"#c9a77f"],
  ["demo-oak-lounge-chair","chair",[.72,.88,.78],"#d6b68e"],
  ["demo-white-oak-console","shelf",[1.6,.42,.4],"#e8dfd1"]
];

for(const [id,type,dims,color] of models){
  const nodes=parts(type,dims);
  const gltf={
    asset:{version:"2.0",generator:"ROOMFIT real-size product model generator"},
    scene:0,scenes:[{nodes:nodes.map((_,i)=>i)}],nodes,
    meshes:[{name:`${id}-unit-cube`,primitives:[{attributes:{POSITION:0,NORMAL:1},indices:2,material:0}]}],
    materials:[
      {name:`${id}-main`,pbrMetallicRoughness:{baseColorFactor:rgb(color),metallicFactor:.02,roughnessFactor:.76}},
      {name:`${id}-detail`,pbrMetallicRoughness:{baseColorFactor:rgb(color),metallicFactor:.01,roughnessFactor:.9}}
    ],
    buffers:[{byteLength:combined.length,uri:cubeBuffer}],
    bufferViews:[
      {buffer:0,byteOffset:0,byteLength:posBuffer.length,target:34962},
      {buffer:0,byteOffset:posBuffer.length,byteLength:normalBuffer.length,target:34962},
      {buffer:0,byteOffset:posBuffer.length+normalBuffer.length,byteLength:indexBuffer.length,target:34963}
    ],
    accessors:[
      {bufferView:0,componentType:5126,count:24,type:"VEC3",min:[-.5,-.5,-.5],max:[.5,.5,.5]},
      {bufferView:1,componentType:5126,count:24,type:"VEC3"},
      {bufferView:2,componentType:5123,count:36,type:"SCALAR"}
    ],extras:{productId:id,type,dimensions:dims,groundAnchor:[.5,1]}
  };
  fs.writeFileSync(path.join(output,`${id}.gltf`),JSON.stringify(gltf));
}
