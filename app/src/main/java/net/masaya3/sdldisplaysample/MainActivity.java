package net.masaya3.sdldisplaysample;

import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,TextToSpeech.OnInitListener{

    private static TextToSpeech tts;
    private Button ttsButton;

    private static String[] haiku = {
    "ふるいけや　っっっ　かわずとびこむ  っっっ   みずのおと ,   っっっ  松尾芭蕉",
    "しずけさや  っっっ   いわにしみいる  っっっ　  せみのこえ ,  っっっ   松尾芭蕉",
    "かきくえば   っっっ  かねがなるなり  っっっ   ほうりゅうじ,  っっっ  まさおかしき",
    "めにはあおば  っっっ  やまホトトギス   っっっ  はつがつお,  っっっ   やまぐちすどう",
    "やせがえる　 っっっ   まけるないっさ　 っっっ   これにあり,  っっっ   こばやしいっさ",
    "なのはなや っっっ    つきはひがしに  っっっ   ひはにしに,  っっっ   よさぶそん",
    "ふるゆきや   っっっ  めいじはとおくに   っっっ  なりにけり, っっっ    なかむらくさたお",
    "なつくさや　 っっっ   ツワモノどもが　  っっっ  ゆめのあと, っっっ    松尾芭蕉",
    "われときて  っっっ   アソベヤおやの  っっっ   ないずずめ,   っっっ  こばやしいっさ",
    "めでたさも  っっっ  　ちうくらいなり　っっっ    おらがはる,   っっっ  こばやしいっさ",
    "ゆきのあさ  っっっ   にのじにのじの　っっっ    げたのあと,  っっっ   でんすてめ",
    "アサガオに　 っっっ   つるべえ取られて　 っっっ   もらいみず ,  っっっ   かがちよめ",
    "はるのうみ   っっっ  ひねもすのたり　 っっっ   のたりかな ,  っっっ   よさぶそん",
    "うめいちりん　 っっっ   いちりんほどの　っっっ    あたたかさ,  っっっ   はっとりらんせつ",
    "これがまあ　 っっっ   ついのすみかか  っっっ  ゆきごしゃく,  っっっ   こばやしいっさ",
    "まつしまや　 っっっ   ああまつしまや  っっっ  　まつしまや,  っっっ   たわらぼう"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tts=new TextToSpeech(this, this);

        ttsButton = findViewById(R.id.button_tts);
        ttsButton.setOnClickListener(this);
        
        //If we are connected to a module we want to start our SdlService
        if(BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            SdlReceiver.queryForConnectedService(this);
        }else if(BuildConfig.TRANSPORT.equals("TCP")) {
            Intent proxyIntent = new Intent(this, SdlService.class);
            startService(proxyIntent);
        }
    }

    @Override
    public void onInit(int status) {
        Log.d("","onInit started");
        if (TextToSpeech.SUCCESS == status) {
            Locale locale = Locale.JAPANESE;
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(locale);
                Log.d("","Init success");
            } else {
                Log.d("", "Error SetLocale");
            }
        } else {
            Log.d("", "Error Init");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tts.shutdown();
    }

    @Override
    public void onClick(View v) {
//        speechText("僕は前澤友作");
    }

    public static void speechText() {
        Random random = new Random();
        int randomValue = random.nextInt(16);
        String contents = haiku[randomValue];
        // TextToSpeechオブジェクトの生成
        if (0 < contents.length()) {
            if (tts.isSpeaking()) {
                // 読み上げ中なら停止
                tts.stop();
            }
            //読み上げられているテキストを確認
            System.out.println("I am in speechText");
            Log.d("",contents);
            //読み上げ開始
            tts.setSpeechRate(0.8f);
            tts.speak(contents, TextToSpeech.QUEUE_FLUSH, null,"");
        }
    }

}
