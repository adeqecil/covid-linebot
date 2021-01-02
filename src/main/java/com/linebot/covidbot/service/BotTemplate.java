package com.linebot.covidbot.service;

import com.linebot.covidbot.model.Hospitals;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.profile.UserProfileResponse;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class BotTemplate {
    public TemplateMessage createButton(String message, String actionTitle, String actionText){

        ButtonsTemplate buttonsTemplate = new ButtonsTemplate(null,null, message,
                Collections.singletonList(new MessageAction(actionTitle, actionText)));

        return new TemplateMessage(actionTitle, buttonsTemplate);
    }
    public TemplateMessage greetingMessage(Source source, UserProfileResponse sender){
        String message = "Halo %s, kamu bisa melihat informasi terkait covid di indonesia.\nSeperti jumlah kasus dan " +
                "rumah sakit yang melayani covid";
        String action = "Cek Daftar Command";

        if (source instanceof GroupSource){
            message = String.format(message, "Group");
        } else if (source instanceof RoomSource){
            message = String.format(message, "Room");
        } else if (source instanceof UserSource){
            message = String.format(message, sender.getDisplayName());
        } else {
            message = "Unknown message Source!";
        }

        return createButton(message, action, action);
    }

    public TemplateMessage carouselEvents(List<Hospitals> hospitals, String province){
        int i;
        String name = null;
        CarouselColumn column;
        List<CarouselColumn> carouselColumns = new ArrayList<>();
        System.out.println(">>Masuk showCarouselEvents method<<<");
        for (i = 0; i < hospitals.size(); i++){

            if (hospitals.get(i).getProvince() == province){
                name = hospitals.get(i).getName();
                System.out.println("isinya hospital for loop "+hospitals.get(i).getName());

                for (int max = 0; max < 10; max++){
                    column = new CarouselColumn(null, name, province, Arrays.asList(new MessageAction("Details",
                            "["+String.valueOf(i)+"]" + " Details : " + name))
                    );
                    carouselColumns.add(column);
                }
            }
        }
        CarouselTemplate carouselTemplate = new CarouselTemplate(carouselColumns);
        System.out.println(">> Keluar carouselEvent isinya carouseltemplate "+ carouselTemplate);
        return new TemplateMessage("Hasil", carouselTemplate);
    }

    public String escape(String text) {
        return  StringEscapeUtils.escapeJson(text.trim());
    }
}
