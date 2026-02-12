package mindustry.world.blocks.environment;

import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.annotations.Annotations.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.world.*;

import static mindustry.Vars.world;

public class Cliff extends Block{
    public float size = 11f;
    public @Load(value = "cliffmask#", length = 256) TextureRegion[] cliffs;

    public Cliff(String name){
        super(name);
        breakable = alwaysReplace = false;
        solid = true;
        cacheLayer = CacheLayer.walls;
        fillsTile = false;
        hasShadow = false;
    }

    @Override
    public void drawBase(Tile tile){
        Draw.color(Tmp.c1.set(tile.floor().mapColor).mul(1.6f));
        Draw.rect(tile.data != 0 ? cliffs[tile.data & 0xff] : region, tile.worldx(), tile.worldy());
        Draw.color();
    }

    @Override
    public void placeEnded(Tile tile, @arc.util.Nullable Unit builder, int rotation, @Nullable Object config){
        int rotationb = 0;
        for(int i = 0; i < 8; i++){
            Tile other = world.tiles.get(tile.x + Geometry.d8[i].x, tile.y + Geometry.d8[i].y);
            if(other != null && !other.floor().hasSurface()){
                rotationb |= (1 << i);
            }
        }
        tile.data = (byte)rotationb;
    }

    @Override
    public int minimapColor(Tile tile){
        return Tmp.c1.set(tile.floor().mapColor).mul(1.2f).rgba();
    }
}
