package com.huntmaster;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import net.runelite.client.util.ImageUtil;

/** Huntmaster's own rank portraits, bundled in the plugin. No remote images. */
final class RankArtwork
{
    private static final Map<String,ImageIcon> CACHE=new LinkedHashMap<String,ImageIcon>(10,.75f,true)
    { @Override protected boolean removeEldestEntry(Map.Entry<String,ImageIcon> eldest){return size()>10;} };
    private RankArtwork(){}
    static synchronized ImageIcon icon(String file)
    {
        if(file==null||!file.matches("[a-z-]{1,40}\\.png"))return null;
        if(CACHE.containsKey(file))return CACHE.get(file);
        try
        {
            BufferedImage source=ImageUtil.loadImageResource(RankArtwork.class,"ranks/"+file);
            double scale=Math.min(1,Math.min(80.0/source.getWidth(),80.0/source.getHeight()));
            ImageIcon image=new ImageIcon(ImageUtil.resizeImage(source,Math.max(1,(int)(source.getWidth()*scale)),Math.max(1,(int)(source.getHeight()*scale))));
            CACHE.put(file,image);return image;
        }catch(RuntimeException ignored){CACHE.put(file,null);return null;}
    }
}
