package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.dto.FeedArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class ArticleExtractorService {

    public FeedArticle enrich(FeedArticle article) {
        try {
            Document doc = Jsoup.connect(article.getLink())
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            String text = doc.body() != null ? doc.body().text() : "";
            if (text.length() > 5000) {
                text = text.substring(0, 5000);
            }
            article.setFullText(text);
        } catch (Exception e) {
            article.setFullText(article.getSummary());
        }
        return article;
    }
}