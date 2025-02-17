package ml.melun.mangaview.mangaview;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Response;

import static ml.melun.mangaview.mangaview.MTitle.base_comic;
import static ml.melun.mangaview.mangaview.MTitle.base_webtoon;

public class MainPageWebtoon {
    String baseUrl;
    public static final String normalNew="일반연재 최신", adultNew="성인웹툰 최신", gayNew="BL/GL 최신", comicNew="일본만화 최신",
            normalBest="일반연재 베스트", adultBest="성인웹툰 베스트", gayBest="BL/GL 베스트", comicBest="일본만화 베스트";
    static final int nn=4,an=5,gn=6,cn=7,nb=8,ab=9,gb=10,cb=11;

    List<Ranking<?>> dataSet;

    public MainPageWebtoon(CustomHttpClient client){
        fetch(client);
    }

    public String getUrl(CustomHttpClient client){
        Response r = client.mget("/site.php?id=1");
        if(r==null) return null;
        if(r.code() == 302 && r.header("Location") != null && r.header("Location").startsWith("https://manatoki")) {
            this.baseUrl = r.header("Location");
        }else
            return null;
        r.close();
        return this.baseUrl;
    }
    public void fetch(CustomHttpClient client){
        if(baseUrl == null || baseUrl.length()==0)
            if(getUrl(client)==null)
                return;
        try {
            Response r = client.get(baseUrl, null);
            String body = r.body().string();
            if(body.contains("Connect Error: Connection timed out")){
                //adblock : try again
                r.close();
                fetch(client);
                return;
            }

            Document d = Jsoup.parse(body);
            Elements boxes = d.select("div.main-box");

            dataSet = new ArrayList<>();

            parseTitle(normalNew, boxes.get(nn).select("a"), base_webtoon);
            parseTitle(adultNew, boxes.get(an).select("a"), base_webtoon);
            parseTitle(gayNew, boxes.get(gn).select("a"), base_webtoon);
            parseTitle(comicNew, boxes.get(cn).select("a"), base_comic);
            parseTitle(normalBest, boxes.get(nb).select("a"), base_webtoon);
            parseTitle(adultBest, boxes.get(ab).select("a"), base_webtoon);
            parseTitle(gayBest, boxes.get(gb).select("a"), base_webtoon);
            parseTitle(comicBest, boxes.get(cb).select("a"), base_comic);

        }catch (Exception e){
            e.printStackTrace();
        }


    }

    public List<Ranking<?>> getDataSet(){
        return this.dataSet;
    }


    public void parseTitle(String title, Elements es, int baseMode){
        Ranking<Title> ranking = new Ranking<>(title);
        Title tmp;
        String idString,idString1,name;
        int id;
        for(Element e : es){
            Element img = e.selectFirst("div.in-subject");
            if(img!=null){
                name = img.ownText();
            }else{
                name = e.ownText();
            }
            System.out.println("   " + name);
            idString = e.attr("href");
            idString1 = idString.substring(idString.lastIndexOf('/')+1);
            id = Integer.parseInt(idString1.substring(idString1.lastIndexOf('=')+1));
            tmp = new Title(name, "", "", null, "", id, baseMode);
            ranking.add(tmp);
        }
        dataSet.add(ranking);
    }

    public static List<Ranking<?>> getBlankDataSet(){
        List<Ranking<?>> dataset = new ArrayList<>();
        dataset.add(new Ranking<>(normalNew));
        dataset.add(new Ranking<>(adultNew));
        dataset.add(new Ranking<>(gayNew));
        dataset.add(new Ranking<>(comicNew));
        dataset.add(new Ranking<>(normalBest));
        dataset.add(new Ranking<>(adultBest));
        dataset.add(new Ranking<>(adultBest));
        dataset.add(new Ranking<>(gayBest));
        return dataset;
    }



}
