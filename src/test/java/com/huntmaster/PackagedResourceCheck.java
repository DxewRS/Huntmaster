package com.huntmaster;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import javax.swing.ImageIcon;

/** Standalone local verification tool; this class is not included in the plugin JAR. */
public final class PackagedResourceCheck
{
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class JarLoader extends URLClassLoader
    {
        private final Map<String,Integer> reads=new HashMap<>();
        JarLoader(File jar)throws Exception{super(new URL[]{jar.toURI().toURL()},PackagedResourceCheck.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException
        {
            synchronized(getClassLoadingLock(name)){
                Class<?> found=findLoadedClass(name);
                // A missing plugin class must fail even when the development tree has it.
                if(found==null&&name.startsWith("com.huntmaster."))found=findClass(name);
                if(found==null)found=super.loadClass(name,false);if(resolve)resolveClass(found);return found;
            }
        }
        @Override public URL getResource(String name)
        {
            if(name.startsWith("com/huntmaster/")){reads.merge(name,1,Integer::sum);return findResource(name);}
            return super.getResource(name);
        }
        int reads(String path){return reads.getOrDefault("com/huntmaster/"+path,0);}
    }
    public static void main(String[] args)throws Exception
    {
        check(args.length==1,"Expected one packaged JAR path");
        File file=new File(args[0]);check(file.isFile(),"Packaged JAR absent");int classCount=0;
        List<String> classNames=new ArrayList<>();
        try(JarFile jar=new JarFile(file)){
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();check(!entry.getName().contains("/test/"),"Unexpected test path");
                if(entry.getName().endsWith(".class"))try(DataInputStream input=new DataInputStream(jar.getInputStream(entry))){
                    check(entry.getName().startsWith("com/huntmaster/"),"Unexpected bundled dependency class");
                    check(!entry.getName().endsWith("Test.class")&&!entry.getName().contains("PackagedResourceCheck"),"Test helper in plugin JAR");
                    check(input.readInt()==0xcafebabe,"Invalid class");input.readUnsignedShort();check(input.readUnsignedShort()<=55,"Requires bytecode newer than Java 11");classCount++;
                    classNames.add(entry.getName().substring(0,entry.getName().length()-6).replace('/','.'));
                }
            }
        }
        int bosses=0,ranks=0;
        try(JarLoader loader=new JarLoader(file)){
            for(String name:classNames)check(loader.loadClass(name).getClassLoader()==loader,"Plugin class resolved outside JAR");
            Class<?> plugin=loader.loadClass("com.huntmaster.HuntmasterPlugin");
            check(net.runelite.client.plugins.Plugin.class.isAssignableFrom(plugin),"Plugin entry point cannot link");
            net.runelite.client.plugins.PluginDescriptor descriptor=plugin.getAnnotation(net.runelite.client.plugins.PluginDescriptor.class);
            check(descriptor!=null&&"Huntmaster".equals(descriptor.name()),"Plugin entry point metadata missing");
            Properties mappings=new Properties();try(InputStream input=loader.getResourceAsStream("com/huntmaster/boss-artwork.properties")){check(input!=null,"Boss mappings absent");mappings.load(input);}
            Class<?> bossType=loader.loadClass("com.huntmaster.BossArtwork"),rankType=loader.loadClass("com.huntmaster.RankArtwork");
            Method boss=bossType.getDeclaredMethod("icon",String.class),rank=rankType.getDeclaredMethod("icon",String.class);boss.setAccessible(true);rank.setAccessible(true);
            for(String name:mappings.stringPropertyNames()){
                ImageIcon icon=(ImageIcon)boss.invoke(null,name);check(icon!=null&&icon.getIconWidth()<=175&&icon.getIconHeight()<=145,"Packaged boss artwork: "+name);bosses++;
                check("jar".equals(loader.getResource("com/huntmaster/bosses/"+mappings.getProperty(name)).getProtocol()),"Resource resolved outside JAR");
            }
            try(JarFile jar=new JarFile(file)){
                Enumeration<JarEntry> entries=jar.entries();while(entries.hasMoreElements()){
                    String path=entries.nextElement().getName();if(!path.startsWith("com/huntmaster/ranks/")||!path.endsWith(".png"))continue;
                    ImageIcon icon=(ImageIcon)rank.invoke(null,path.substring(path.lastIndexOf('/')+1));
                    check(icon!=null&&icon.getIconWidth()<=80&&icon.getIconHeight()<=80,"Packaged rank portrait");ranks++;
                }
            }
            check(loader.getResource("com/huntmaster/panel_icon.png")!=null,"Navigation icon absent");
            rank.invoke(null,"missing-rank.png");int before=loader.reads("ranks/missing-rank.png");rank.invoke(null,"missing-rank.png");
            check(before>0&&before==loader.reads("ranks/missing-rank.png"),"Missing rank lookup was repeated");
            Field files=bossType.getDeclaredField("FILES");files.setAccessible(true);((Properties)files.get(null)).setProperty("Missing boss","missing-boss.png");
            boss.invoke(null,"Missing boss");before=loader.reads("bosses/missing-boss.png");boss.invoke(null,"Missing boss");
            check(before>0&&before==loader.reads("bosses/missing-boss.png"),"Missing boss lookup was repeated");
            for(int i=0;i<100;i++)rank.invoke(null,"future-"+(char)('a'+i/26)+(char)('a'+i%26)+".png");
            Field cache=rankType.getDeclaredField("CACHE");cache.setAccessible(true);check(((Map<?,?>)cache.get(null)).size()<=10,"Missing artwork cache exceeded bound");
            Class<?> release=loader.loadClass("com.huntmaster.ReleaseInfo");Field version=release.getDeclaredField("VERSION"),build=release.getDeclaredField("BUILD");version.setAccessible(true);build.setAccessible(true);
            check(ReleaseInfo.VERSION.equals(version.get(null))&&ReleaseInfo.BUILD.equals(build.get(null)),"Packaged release labels stale");
        }
        check(bosses==63&&ranks==10&&classCount>0,"Incomplete package");
        System.out.println("{\"status\":\"PASS\",\"bossMappings\":"+bosses+",\"rankPortraits\":"+ranks+",\"classCount\":"+classCount+",\"resourceProtocol\":\"jar\",\"maximumJavaClassVersion\":55,\"missingArtworkLookupsCached\":true}");
    }
}
