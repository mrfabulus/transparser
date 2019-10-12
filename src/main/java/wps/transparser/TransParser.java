package wps.transparser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransParser
{
    public static void main(String[] args) throws IOException
    {
        File directory = new File("wps-hu/");
        File newDirectory = new File("wps-hu-new/");
        File inambigousCorrectedFile = new File("inambigous_corrected.txt");
        BufferedWriter inambigousFile = new BufferedWriter(new FileWriter("inambigous.txt"));
        Map<String, String> CorrectedList = new HashMap<>();
        Map<String, String> TranslationList = new HashMap<>();
        Map<String, String> InambigousList = new HashMap<>();

        System.out.println("Reading corrected inambigous translations from inambigous_corrected.txt...");

        if(Files.exists(inambigousCorrectedFile.toPath()))
        {

            String content = new String(Files.readAllBytes(inambigousCorrectedFile.toPath()), Charset.forName("UTF-8"));

            String[] data = content.split("\r\n\r\n\r\n");

            for(String entry : data)
            {
                String[] split = entry.split("\t->\t");

                if(split.length == 2)
                    CorrectedList.put(unwrapEntity(split[0]), unwrapEntity(split[1]));
            }

            System.out.println("Corrected translations read!");
        }
        else
        {
            System.out.println("inambigous_corrected.txt not found.");
        }

        System.out.println("Reading existing translations...");
        List<File> files = Arrays.asList(directory.listFiles());
        Document doc;
        Elements elements;

        for (File entry : files)
        {
            System.out.println(entry.getName());
            doc = Jsoup.parse(readFile(entry, Charset.forName("UTF-8")),"UTF-8","", Parser.xmlParser());
            doc.outputSettings().escapeMode(Entities.EscapeMode.base).charset("UTF-8");
            elements = doc.select("message");

            for(int i = 0; i < elements.size(); i++)
            {
                Element source = elements.get(i).select("source").get(0);
                Element trans = elements.get(i).select("translation").get(0);

                String sourceStr = unwrapElement(source);
                String transStr = unwrapElement(trans);

                if((!trans.hasAttr("type") || !trans.attr("type").equalsIgnoreCase("unfinished"))
                        && (trans != null && transStr != null)
                        && !CorrectedList.containsKey(sourceStr))
                {
                    if(!TranslationList.containsKey(sourceStr) || TranslationList.get(sourceStr).equals(transStr))
                    {
                        if(!TranslationList.containsKey(sourceStr))
                            TranslationList.put(sourceStr, transStr);
                    }
                    else if(!InambigousList.containsKey(sourceStr))
                    {
                        InambigousList.put(sourceStr, TranslationList.get(sourceStr) + "\t in: " + entry.getPath() + "\t|\t" + transStr );
                    }
                    else
                    {
                        String value = InambigousList.remove(sourceStr);
                        value += "\t|\t" + transStr;
                        InambigousList.put(sourceStr, value);
                    }
                }
            }
        }

        System.out.println("Exporting inambigous translations... Press ENTER to continue!");
        System.in.read();

        for(Map.Entry<String, String> entry : InambigousList.entrySet())
        {
            inambigousFile.write(wrapEntity(entry.getKey() + "\t->\t" + entry.getValue().replaceAll("\n", "").trim() + "\r\n\r\n"));
            TranslationList.remove(entry.getKey());
        }

        inambigousFile.flush();
        inambigousFile.close();

        System.out.println("List of inambigous translations saved to inambigous.txt");
        System.out.println("Writing known translations from dictionary... Press ENTER to continue!");
        System.in.read();

        if(!newDirectory.exists())
            newDirectory.mkdir();
        else
        {
            for (File entry : newDirectory.listFiles())
            {
                entry.delete();
            }
        }

        for (File entry : files)
        {
            doc = Jsoup.parse(readFile(entry, Charset.forName("UTF-8")), "UTF-8", "", Parser.xmlParser());
            doc.outputSettings().escapeMode(Entities.EscapeMode.base).prettyPrint(false).indentAmount(0);
            elements = doc.select("message");

            for(int i = 0; i < elements.size(); i++)
            {
                Element source = elements.get(i).select("source").get(0);
                Element trans = elements.get(i).select("translation").get(0);

                String sourceStr = unwrapElement(source);
                String transStr = unwrapElement(trans);

                if(source != null & sourceStr != null)
                    source.text(unwrapEntity(sourceStr));

                if((trans.hasAttr("type") && trans.attr("type").equalsIgnoreCase("unfinished")))
                {
                    if(TranslationList.containsKey(sourceStr))
                    {
                        trans.text(unwrapEntity(TranslationList.get(sourceStr)));
                        if(!TranslationList.get(sourceStr).isEmpty())
                            trans.removeAttr("type");
                        System.out.println(wrapEntity("Translation for: " + sourceStr + "\tin: " + entry.getName() + "\twas saved as: " + TranslationList.get(sourceStr)));
                    }

                    if(CorrectedList.containsKey(sourceStr))
                    {
                        trans.text(unwrapEntity(CorrectedList.get(sourceStr)));
                        if(!CorrectedList.get(sourceStr).isEmpty())
                            trans.removeAttr("type");
                        System.out.println(wrapEntity("Translation for: " + sourceStr + "\tin: " + entry.getName() + "\twas saved as: " + CorrectedList.get(sourceStr)));
                    }
                }
                else if(CorrectedList.containsKey(sourceStr) && !CorrectedList.get(sourceStr).equals(transStr))
                {
                    trans.text(unwrapEntity(CorrectedList.get(sourceStr)));
                    System.out.println(wrapEntity("Translation for: " + sourceStr + "\tin: " + entry.getName() + "\twas fixed to: " + CorrectedList.get(sourceStr) + "\tfrom: " + transStr));
                }
            }

            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(newDirectory.getPath() + "/" + entry.getName()), Charset.forName("UTF-8")));
            bw.write(wrapEntity(doc.toString()));
            bw.flush();
            bw.close();
        }

        System.out.println("Done");
    }

    public static String unwrapElement(Element element)
    {
        return unwrapEntity(element.toString().replaceAll("<" + element.tagName() + "([^>]+?)>", "").replace("<" + element.tagName() + ">", "").replace("</" + element.tagName() + ">", "").trim());
    }

    public static String unwrapEntity(String str)
    {
        return str.replaceAll("&([^;]+?);", "!#$1;");
    }

    public static String wrapEntity(String str)
    {
        return str.replaceAll("!#([^;]+?);", "&$1;");
    }

    static ByteArrayInputStream readFile(File file, Charset encoding) throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(file.getPath()));
        return new ByteArrayInputStream((unwrapEntity(new String(encoded, encoding)).getBytes()));
    }
}
