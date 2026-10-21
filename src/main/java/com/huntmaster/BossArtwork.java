package com.huntmaster;

import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.swing.ImageIcon;
import net.runelite.client.util.ImageUtil;

/** Packaged OSRS game renders. No requests, filesystem reads, or player-selected URLs. */
final class BossArtwork
{
    private static final Properties FILES=new Properties();
    private static final Map<String,ImageIcon> CACHE=new LinkedHashMap<String,ImageIcon>(8,.75f,true)
    {
        @Override protected boolean removeEldestEntry(Map.Entry<String,ImageIcon> eldest){return size()>8;}
    };
    static
    {
        try(InputStream input=BossArtwork.class.getResourceAsStream("boss-artwork.properties"))
        {if(input!=null)FILES.load(input);}
        catch(IOException ignored){/* Missing artwork never prevents displaying a Hunt. */}
    }
    private BossArtwork(){}
    static synchronized ImageIcon icon(String boss)
    {
        String file=FILES.getProperty(boss);if(file==null)return null;
        if(CACHE.containsKey(file))return CACHE.get(file);
        try
        {
            BufferedImage image=ImageUtil.loadImageResource(BossArtwork.class,"bosses/"+file);
            double scale=Math.min(1,Math.min(175.0/image.getWidth(),145.0/image.getHeight()));
            ImageIcon icon=new ImageIcon(ImageUtil.resizeImage(image,Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale))));
            CACHE.put(file,icon);return icon;
        }catch(RuntimeException ignored){CACHE.put(file,null);return null;}
    }
}
