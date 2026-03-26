package com.agent.video.generator.trendsvideo.service;

import com.agent.video.generator.trendsvideo.dto.TopicGroup;
import com.agent.video.generator.trendsvideo.dto.VideoScriptJson;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ScriptGeneratorService {

    public VideoScriptJson generateEnglishScript(TopicGroup topic, String mergedStory) {
        VideoScriptJson script = new VideoScriptJson();
        script.setTitle("Top Story: " + topic.getTopic());
        script.setLanguage("en");
        script.setNarrationStyle("professional news");

        List<VideoScriptJson.SceneJson> scenes = new ArrayList<>();

        VideoScriptJson.SceneJson intro = new VideoScriptJson.SceneJson();
        intro.setHeading("Introduction");
        intro.setNarration("Here is the latest update on " + topic.getTopic() + ". This short video gives a quick and clear overview in English.");
        intro.setVisualPrompt("news intro animation, global newsroom, modern graphics");
        intro.setDurationSeconds(6);
        intro.setImageHints(List.of("newsroom", "headline", "breaking news"));
        scenes.add(intro);

        VideoScriptJson.SceneJson details = new VideoScriptJson.SceneJson();
        details.setHeading("Key Update");
        details.setNarration(trim(mergedStory, 500));
        details.setVisualPrompt("related headline montage, article snippets, world map, subtle motion graphics");
        details.setDurationSeconds(15);
        details.setImageHints(List.of(topic.getTopic(), "news event"));
        scenes.add(details);

        VideoScriptJson.SceneJson close = new VideoScriptJson.SceneJson();
        close.setHeading("Closing");
        close.setNarration("That was the latest summary on " + topic.getTopic() + ". Stay tuned for more top updates.");
        close.setVisualPrompt("clean outro, subscribe prompt, modern end card");
        close.setDurationSeconds(5);
        close.setImageHints(List.of("outro", "news update"));
        scenes.add(close);

        script.setScenes(scenes);
        return script;
    }

    private String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}