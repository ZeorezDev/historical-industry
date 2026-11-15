// Convert the editable Blockbench projects to 1.20.1 baked models and client animation layers.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const assets = path.join(root,'src/main/resources/assets/historical_industry');
for (const filename of fs.readdirSync(path.join(root,'models')).filter(n => n.endsWith('.bbmodel'))) {
    const name = path.basename(filename,'.bbmodel');
    const model = JSON.parse(fs.readFileSync(path.join(root,'models',filename),'utf8'));
    const texture = 'historical_industry:block/'+name;
    const dir = path.join(assets,'models/block');
    const {width,height} = model.resolution;
    const elements = model.elements.map(cube => {
        const faces = {};
        for (const [side,face] of Object.entries(cube.faces)) {
            if (face.texture == null) continue;
            faces[side] = {uv:face.uv.map((v,i) => v*16/(i%2 ? height : width)),texture:'#machine'};
            if (face.rotation) faces[side].rotation=face.rotation;
        }
        const element = {name:cube.name,from:cube.from,to:cube.to,faces};
        const axis = cube.rotation?.findIndex(v => v!==0) ?? -1;
        if (axis>=0) element.rotation = {axis:'xyz'[axis],angle:cube.rotation[axis],origin:cube.origin,rescale:false};
        return element;
    });
    const save = (suffix,selected) => fs.writeFileSync(path.join(dir,name+suffix+'.json'),
        JSON.stringify({parent:'minecraft:block/block',textures:{particle:texture,machine:texture},elements:selected},null,2)+'\n');
    save('',elements);
    const drive = model.groups.find(group => group.name==='drive');
    const moving = new Set(model.outliner.find(group => group.uuid===drive.uuid).children);
    save('_running',elements.filter((_,i) => !moving.has(model.elements[i].uuid)));
    save('_drive',elements.filter((_,i) => moving.has(model.elements[i].uuid)));
    const source=model.textures[0].source;
    if (!source.startsWith('data:image/png;base64,')) throw Error('Missing embedded PNG: '+filename);
    fs.writeFileSync(path.join(assets,'textures/block',name+'.png'),Buffer.from(source.split(',')[1],'base64'));
}
