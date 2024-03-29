package com.linebot.covidbot.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linebot.covidbot.model.Hospitals;
import com.linebot.covidbot.model.KasusIndonesia;
import com.linebot.covidbot.model.LineEventsModel;
import com.linebot.covidbot.service.BotService;
import com.linebot.covidbot.service.BotTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.JoinEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.ReplyEvent;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.message.FlexMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.flex.container.FlexContainer;
import com.linecorp.bot.model.objectmapper.ModelObjectMapper;
import com.linecorp.bot.model.profile.UserProfileResponse;
import net.minidev.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
public class LineBotController {

    @Autowired
    @Qualifier("lineSignatureValidator")
    private LineSignatureValidator lineSignatureValidator;

    @Autowired
    private BotService botService;

    @Autowired
    private BotTemplate botTemplate;

    private final List<String> pulau = Arrays.asList("Jawa", "Kalimantan", "Sulawesi", "Sumatera", "Bali");

    private final List<String> jawa = Arrays.asList("Jawa Timur", "Jawa Barat", "Jawa Tengah", "DI Yogyakarta", "DKI " +
            "Jakarta");
    private final List<String> kalimantan = Arrays.asList("Kalimantan Timur", "Kalimantan Barat", "Kalimantan Selatan",
            "Kalimantan Utara");
    private final List<String> sulawesi = Arrays.asList("Sulawesi Barat", "Sulawesi Selatan", "Sulawesi Tengah", "Sulawesi " +
            "Utara");
    private final List<String> sumatera = Arrays.asList("Sumatera Utara", "Sumatera Selatan", "Sumatera Barat");
    private UserProfileResponse sender = null;
    private List<Hospitals> hospitals = null;
    private KasusIndonesia kasusIndonesia = null;

    @RequestMapping(value = "/webhook", method = RequestMethod.POST)
    public ResponseEntity<String> callback(@RequestHeader("X-Line-Signature") String xLineSignature,
                                           @RequestBody String eventsPayload){
        try {
            //validate line signature
            /*
            if (!lineSignatureValidator.validateSignature(eventsPayload.getBytes(), xLineSignature)) {
                throw new RuntimeException("Invalid Signature Validation");
            }
             */

            System.out.println(eventsPayload);
            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            LineEventsModel eventsModel = objectMapper.readValue(eventsPayload, LineEventsModel.class);

            eventsModel.getEvents().forEach((event) -> {
                if (event instanceof JoinEvent || event instanceof FollowEvent){
                    String replyToken = ((ReplyEvent) event).getReplyToken();
                    handleJoinOrFollowEvent(replyToken, event.getSource());
                } else if (event instanceof MessageEvent){
                    handleMessageEvent((MessageEvent) event);
                }
            });

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IOException e){
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private void greetingMessage(String replyToken, Source source, String additionalMessage){
        if(sender == null){
            String senderId = source.getSenderId();
            sender = botService.getProfile(senderId);
        }

        TemplateMessage greetingMessage = botTemplate.greetingMessage(source, sender);

        if(additionalMessage != null){
            List<Message> messages = new ArrayList<>();
            messages.add(new TextMessage(additionalMessage));
            messages.add(greetingMessage);
            botService.reply(replyToken, messages);
        } else {
            botService.reply(replyToken, greetingMessage);
        }
    }

    private void handleJoinOrFollowEvent(String replyToken, Source source){
        greetingMessage(replyToken, source, null);
    }

    private void handleMessageEvent(MessageEvent event){
        String replyToken = event.getReplyToken();
        MessageContent content = event.getMessage();
        Source source = event.getSource();
        String senderId = source.getSenderId();
        sender = botService.getProfile(senderId);

        if(content instanceof TextMessageContent){
            handleTextMessage(replyToken, (TextMessageContent) content, source);
        } else {
            greetingMessage(replyToken, source, null);
        }
    }

    private void handleTextMessage(String replyToken, TextMessageContent content, Source source){
        if (source instanceof GroupSource){
            handleGroupChats(replyToken, content.getText(), ((GroupSource) source).getGroupId());
        } else if (source instanceof RoomSource){
            handleRoomChats(replyToken, content.getText(), ((RoomSource) source).getRoomId());
        } else if (source instanceof UserSource){
            handleOneOnOneChats(replyToken, content.getText());
        } else {
            botService.replyText(replyToken, "Unknown message source");
        }
    }

    private void handleGroupChats(String replyToken, String textMessage, String groupId){
        String msgText = textMessage.toLowerCase();
        if (msgText.equals("a")
                || msgText.equals("b")
        ) {
            processText(replyToken, msgText);
        }else if (msgText.equals("cek daftar command")){
            processDaftarCommand(replyToken, msgText);
        } else if (msgText.equals("sumatera")
                || msgText.equals("kalimantan")
                || msgText.equals("sulawesi")
                || msgText.equals("jawa")
                || msgText.equals("bali")) {
            processPulau(replyToken, textMessage);
        } else if (msgText.equals("sumatera utara")
                || msgText.equals("sumatera selatan")
                || msgText.equals("sumatera barat")
                || msgText.equals("kalimantan timur")
                || msgText.equals("kalimantan barat")
                || msgText.equals("kalimantan selatan")
                || msgText.equals("kalimantan utara")
                || msgText.equals("sulawesi barat")
                || msgText.equals("sulawesi selatan")
                || msgText.equals("sulawesi tengah")
                || msgText.equals("sulawesi utara")
                || msgText.equals("jawa timur")
                || msgText.equals("jawa barat")
                || msgText.equals("jawa tengah")
                || msgText.equals("dki jakarta")
                || msgText.equals("di yogyakarta")
                || msgText.equals("allbali")) {
            showCarouselEvents(replyToken, textMessage);
        } else if (msgText.contains("details")) {
            showHospitalDetails(replyToken, textMessage);
        } else {
            handleFallbackMessage(replyToken, new GroupSource(groupId, sender.getUserId()));
        }
    }

    private void handleRoomChats(String replyToken, String textMessage, String roomId){
        String msgText = textMessage.toLowerCase();
        if (msgText.equals("a")
                || msgText.equals("b")
        ) {
            processText(replyToken, msgText);
        }else if (msgText.equals("cek daftar command")){
            processDaftarCommand(replyToken, msgText);
        } else if (msgText.equals("sumatera")
                || msgText.equals("kalimantan")
                || msgText.equals("sulawesi")
                || msgText.equals("jawa")
                || msgText.equals("bali")) {
            processPulau(replyToken, textMessage);
        } else if (msgText.equals("sumatera utara")
                || msgText.equals("sumatera selatan")
                || msgText.equals("sumatera barat")
                || msgText.equals("kalimantan timur")
                || msgText.equals("kalimantan barat")
                || msgText.equals("kalimantan selatan")
                || msgText.equals("kalimantan utara")
                || msgText.equals("sulawesi barat")
                || msgText.equals("sulawesi selatan")
                || msgText.equals("sulawesi tengah")
                || msgText.equals("sulawesi utara")
                || msgText.equals("jawa timur")
                || msgText.equals("jawa barat")
                || msgText.equals("jawa tengah")
                || msgText.equals("dki jakarta")
                || msgText.equals("di yogyakarta")
                || msgText.equals("allbali")) {
            showCarouselEvents(replyToken, textMessage);
        } else if (msgText.contains("details")) {
            showHospitalDetails(replyToken, textMessage);
        } else {
            handleFallbackMessage(replyToken, new RoomSource(roomId, sender.getUserId()));
        }
    }

    private void handleOneOnOneChats(String replyToken, String textMessage) {
        String msgText = textMessage.toLowerCase();
        System.out.println(">>>Masuk method handleOneOnOneChats ini isi msgText lowercase <<<"+msgText);
        if (msgText.equals("a")
                || msgText.equals("b")
        ) {
            processText(replyToken, msgText);
        }else if (msgText.equals("cek daftar command")){
            processDaftarCommand(replyToken, msgText);
        } else if (msgText.equals("sumatera")
                || msgText.equals("kalimantan")
                || msgText.equals("sulawesi")
                || msgText.equals("jawa")
                || msgText.equals("bali")) {
            processPulau(replyToken, textMessage);
        } else if (msgText.equals("sumatera utara")
                || msgText.equals("sumatera selatan")
                || msgText.equals("sumatera barat")
                || msgText.equals("kalimantan timur")
                || msgText.equals("kalimantan barat")
                || msgText.equals("kalimantan selatan")
                || msgText.equals("kalimantan utara")
                || msgText.equals("sulawesi barat")
                || msgText.equals("sulawesi selatan")
                || msgText.equals("sulawesi tengah")
                || msgText.equals("sulawesi utara")
                || msgText.equals("jawa timur")
                || msgText.equals("jawa barat")
                || msgText.equals("jawa tengah")
                || msgText.equals("dki jakarta")
                || msgText.equals("di yogyakarta")
                || msgText.equals("allbali")) {
            showCarouselEvents(replyToken, textMessage);
        } else if (msgText.contains("details")) {
            showHospitalDetails(replyToken, textMessage);
        } else {
            handleFallbackMessage(replyToken, new UserSource(sender.getUserId()));
        }
    }

    private void handleFallbackMessage(String replyToken, Source source){
        greetingMessage(replyToken, source, "Halo " + sender.getDisplayName() + ", botnya belum ngerti commandnya nih" +
                ". Coba cek petunjuk ya!");
    }

    private void processText(String replyToken, String messageText){
        String[] words = messageText.trim().split("\\s+");
        String intent = words[0];

        if (intent.equalsIgnoreCase("a")){
            handleListHospitals(replyToken, messageText);
        } else if (intent.equalsIgnoreCase("b")){
            handleJumlahKasusTotal(replyToken, messageText);
        }
    }

    private void processDaftarCommand(String replyToken, String messageText){
        String daftarCommand = "\nA. Daftar Rumah Sakit\nB. Cek Kasus Indonesia";

        botService.replyText(replyToken,
                "Berikut daftar command yang bisa digunakan.\nKirim pilihan abjadnya aja ya, contoh: A\n" +daftarCommand);
    }

    private void processPulau(String replyToken, String messageText){
        List<String> provinsi = new ArrayList<>();
        System.out.println(">>>masuk ke processPulau method<<<");
        int i;
        if (messageText.toLowerCase().equals("jawa")){
            provinsi = jawa;
        } else if (messageText.toLowerCase().equals("kalimantan")){
            provinsi = kalimantan;
        } else if (messageText.toLowerCase().equals("sumatera")){
            provinsi = sumatera;
        } else if (messageText.toLowerCase().equals("sulawesi")){
            provinsi = sulawesi;
        }
        String hasilProvinsi = "\n";
        for (i = 0; i < provinsi.size(); i++){
            hasilProvinsi += i+1 + ". " + provinsi.get(i) + "\n";
        }
        botService.replyText(replyToken, "Silakan pilih provinsi berikut.\nContoh: jawa timur\n\n" + hasilProvinsi);
    }

    private void handleListHospitals(String replyToken, String words){
        String daftarPulau = "\n";
        int i;
        for (i = 0; i < pulau.size(); i++){
            daftarPulau += i+1 + ". " + pulau.get(i) + "\n";
        }
        botService.replyText(replyToken, "Silakan ketik salah satu pulau berikut.\nContoh: jawa\n\n" +
                daftarPulau);
    }


    private void handleJumlahKasusTotal(String replyToken, String words){
        if(kasusIndonesia == null){
            getJumlahKasusTotal();
        }

        Date date = new Date(kasusIndonesia.getTimestamp());
        DateFormat formatnya = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        String formatted = formatnya.format(date);
        String msg = String.format("Berikut update kasus terkini.\n\n%1$s\nInfected: %2$s\nRecovered: %3$s\nFatal: " +
                "%4$s\n\nLast Update %5$s", kasusIndonesia.getName(), kasusIndonesia.getInfected(),
                kasusIndonesia.getRecovered(), kasusIndonesia.getFatal(), formatted);
        botService.replyText(replyToken, msg);
    }

    private void showCarouselEvents(String replyToken, String province){
        showCarouselEvents(replyToken, province, null);
    }

    private void showCarouselEvents(String replyToken, String province, String additionalInfo){
        if(hospitals == null){
            getHospitals();
        }
        TemplateMessage carouselEvents = botTemplate.carouselEvents(hospitals, province);
        System.out.println(">>> Isi carouselEvents "+carouselEvents);
        if (additionalInfo == null){
            botService.reply(replyToken, carouselEvents);
            return;
        }

        List<Message> messageList = new ArrayList<>();
        messageList.add(new TextMessage(additionalInfo));
        messageList.add(carouselEvents);
        botService.reply(replyToken, messageList);
    }

    private void getJumlahKasusTotal(){
        //GET method for json
        String URI = "https://dekontaminasi.com/api/id/covid19/stats";
        System.out.println("URI: " + URI);

        try (CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()){
            client.start();
            //HTTP to retrive data
            HttpGet get = new HttpGet(URI);

            Future<HttpResponse> future = client.execute(get, null);
            HttpResponse responseGet = future.get();
            System.out.println("HTTP executed");
            System.out.println("HTTP Status response: " + responseGet.getStatusLine().getStatusCode());

            //response from get request
            InputStream inputStream = responseGet.getEntity().getContent();
            String encoding = StandardCharsets.UTF_8.name();

            String jsonResponse = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            System.out.println("Got Result");
            System.out.println(inputStream);

            ObjectMapper objectMapper = new ObjectMapper();
            //System.out.println(objectMapper.readValue(jsonResponse, Hospitals.class));
            kasusIndonesia = objectMapper.readValue(jsonResponse, KasusIndonesia.class);

            System.out.println(">> Keluar gethospital isinya hospitals "+kasusIndonesia.getName());

        } catch (InterruptedException | ExecutionException | IOException e){
            throw new RuntimeException(e);
        }
    }

    private void getHospitals(){
        //GET method for json
        String URI = "https://dekontaminasi.com/api/id/covid19/hospitals";
        System.out.println("URI: " + URI);

        try (CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()){
            client.start();
            //HTTP to retrive data
            HttpGet get = new HttpGet(URI);

            Future<HttpResponse> future = client.execute(get, null);
            HttpResponse responseGet = future.get();
            System.out.println("HTTP executed");
            System.out.println("HTTP Status response: " + responseGet.getStatusLine().getStatusCode());

            //response from get request
            InputStream inputStream = responseGet.getEntity().getContent();
            String encoding = StandardCharsets.UTF_8.name();

            String jsonResponse = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

            System.out.println("Got Result");
            System.out.println(inputStream);

            ObjectMapper objectMapper = new ObjectMapper();
            //System.out.println(objectMapper.readValue(jsonResponse, Hospitals.class));
            hospitals = Arrays.asList(objectMapper.readValue(jsonResponse, Hospitals[].class));

            System.out.println(">> Keluar gethospital isinya hospitals "+hospitals.get(1).getAddress());

        } catch (InterruptedException | ExecutionException | IOException e){
            throw new RuntimeException(e);
        }
    }

    private void showHospitalDetails(String replyToken, String userTxt){

        try {
            if (hospitals == null){
                getHospitals();
            }

            int hospitalsIndex = Integer.parseInt(userTxt.substring(1,3));
            Hospitals details = hospitals.get(hospitalsIndex);
            String nomorHp = null;

            ClassLoader classLoader = getClass().getClassLoader();
            String encoding = StandardCharsets.UTF_8.name();
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            Resource resource = resourceLoader.getResource("flex_hospital.json");
            String flexTemplate = IOUtils.toString(classLoader.getResourceAsStream("flex_hospital.json"),
                    StandardCharsets.UTF_8);
            System.out.println(">>> INI INDEX HOSPITAL "+hospitalsIndex+ " SAMA DETAILS "+details.getPhone());
            if(details.getPhone() == null){
                nomorHp = "Nomor tidak ada";
            } else {
                nomorHp = details.getPhone();
            }
            flexTemplate = String.format(flexTemplate,
                    //searchImage(botTemplate.escape((details.getName()))),
                    botTemplate.escape(details.getName()),
                    botTemplate.escape(details.getAddress()),
                    botTemplate.escape(details.getRegion()),
                    nomorHp,
                    cekGoogle(botTemplate.escape(details.getName()))
            );

            ObjectMapper objectMapper = ModelObjectMapper.createNewObjectMapper();
            FlexContainer flexContainer = objectMapper.readValue(flexTemplate, FlexContainer.class);
            botService.reply(replyToken, new FlexMessage("Covid Indonesia", flexContainer));
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    private String searchImage(String keyword){
        String userAgent = "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.116 Safari/537.36";
        keyword = keyword.replace(' ', '+');
        String url = "https://www.google.com/search?site=imghp&tbm=isch&source=hp&q="+keyword+"&gws_rd=cr";
        String imageUrl = null;

        try {
            /*Jsoup.connect(url).userAgent(userAgent).get()
                    .select("h3.r").select("a")
                    .stream()
                    .limit(1)
                    .map(l -> l.attr("href")).forEach(System.out::println);*/
            imageUrl = Jsoup.connect(url).userAgent(userAgent).get().select("h3.r").select("a").attr("href");

            System.out.println(">>> INI ISI IMAGEURL "+imageUrl);
            /*for (int i=0; i < 1; i++){
                imageUrl = links.get(i).attr("href");
            }*/

        } catch (IOException e) {
            e.printStackTrace();
        }

        return imageUrl;
    }

    private String cekGoogle(String hospitalName){
        hospitalName = hospitalName.replace(' ', '+');
        String url = "https://www.google.com/search?q="+hospitalName;
        return url;
    }
}
