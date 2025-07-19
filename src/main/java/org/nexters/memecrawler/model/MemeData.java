package org.nexters.memecrawler.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class MemeData {
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("origin")
    private String origin;
    
    @JsonProperty("popularity")
    private String popularity;
    
    @JsonProperty("parodied_memes")
    private List<String> parodiedMemes;
    
    @JsonProperty("trending_period")
    private String trendingPeriod;
    
    @JsonProperty("related_image_urls")
    private List<String> relatedImageUrls;
    
    @JsonProperty("namuwiki_source")
    private String namuwikiSource;
    
    @JsonProperty("related_keywords")
    private List<String> relatedKeywords;

    public MemeData() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getPopularity() { return popularity; }
    public void setPopularity(String popularity) { this.popularity = popularity; }

    public List<String> getParodiedMemes() { return parodiedMemes; }
    public void setParodiedMemes(List<String> parodiedMemes) { this.parodiedMemes = parodiedMemes; }

    public String getTrendingPeriod() { return trendingPeriod; }
    public void setTrendingPeriod(String trendingPeriod) { this.trendingPeriod = trendingPeriod; }

    public List<String> getRelatedImageUrls() { return relatedImageUrls; }
    public void setRelatedImageUrls(List<String> relatedImageUrls) { this.relatedImageUrls = relatedImageUrls; }

    public String getNamuwikiSource() { return namuwikiSource; }
    public void setNamuwikiSource(String namuwikiSource) { this.namuwikiSource = namuwikiSource; }

    public List<String> getRelatedKeywords() { return relatedKeywords; }
    public void setRelatedKeywords(List<String> relatedKeywords) { this.relatedKeywords = relatedKeywords; }
}