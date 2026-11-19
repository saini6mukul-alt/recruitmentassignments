#!/usr/bin/env python3
"""Generates app/src/main/assets/phrasebook.json.

Each line: hindi variants separated by '/'  ||  English  ||  short Hindi tip (optional)
Variants cover boy/girl forms (रहा/रही, गया/गई) and common spellings.
Run: python3 tools/gen_phrasebook.py
"""
import json, os

RAW = """
# --- Greetings and manners ---
नमस्ते/नमस्कार || Hello. || नमस्ते = Hello
सुप्रभात || Good morning. || सुप्रभात = Good morning
शुभ रात्रि || Good night. || शुभ रात्रि = Good night
आप कैसे हो/आप कैसे हैं/आप कैसी हो/तुम कैसे हो/तुम कैसी हो || How are you? || कैसे हो = How are you
मैं ठीक हूँ/मैं अच्छा हूँ/मैं अच्छी हूँ || I am fine. || ठीक = fine
धन्यवाद/शुक्रिया || Thank you. || धन्यवाद = Thank you
माफ़ करना/माफ़ कीजिए/सॉरी || Sorry. || माफ़ करना = Sorry
कोई बात नहीं || It is okay. || कोई बात नहीं = It is okay
फिर मिलेंगे || See you again. || फिर मिलेंगे = See you again
अलविदा/बाय || Goodbye. || अलविदा = Goodbye
आपसे मिलकर अच्छा लगा/आपसे मिलकर खुशी हुई || Nice to meet you. || मिलकर अच्छा लगा = Nice to meet you
जन्मदिन मुबारक हो/हैप्पी बर्थडे || Happy birthday! || जन्मदिन = birthday
कृपया मेरी मदद करो/प्लीज मेरी मदद करो/मेरी मदद करो || Please help me. || मदद = help
# --- About me ---
मेरा नाम क्या है || What is my name? || नाम = name
आपका नाम क्या है/तुम्हारा नाम क्या है || What is your name? || नाम = name
मैं सात साल का हूँ/मैं सात साल की हूँ || I am seven years old. || साल = years
मैं पाँच साल का हूँ/मैं पाँच साल की हूँ || I am five years old. || पाँच = five
मैं छह साल का हूँ/मैं छह साल की हूँ || I am six years old. || छह = six
मैं आठ साल का हूँ/मैं आठ साल की हूँ || I am eight years old. || आठ = eight
मैं नौ साल का हूँ/मैं नौ साल की हूँ || I am nine years old. || नौ = nine
मैं दस साल का हूँ/मैं दस साल की हूँ || I am ten years old. || दस = ten
मैं भारत में रहता हूँ/मैं भारत में रहती हूँ || I live in India. || रहता हूँ = live
मैं लड़का हूँ || I am a boy. || लड़का = boy
मैं लड़की हूँ || I am a girl. || लड़की = girl
मेरा एक भाई है || I have a brother. || भाई = brother
मेरी एक बहन है || I have a sister. || बहन = sister
यह मेरी मम्मी हैं || This is my mother. || मम्मी = mother
यह मेरे पापा हैं || This is my father. || पापा = father
मैं अपने परिवार से प्यार करता हूँ/मैं अपने परिवार से प्यार करती हूँ || I love my family. || प्यार करना = love
मुझे मम्मी की याद आ रही है || I miss my mother. || याद आना = miss
# --- Needs ---
मुझे पानी चाहिए || I want water. || चाहिए = want
मुझे पानी नहीं चाहिए || I do not want water. || नहीं चाहिए = do not want
मुझे भूख लगी है || I am hungry. || भूख = hungry
मुझे प्यास लगी है || I am thirsty. || प्यास = thirsty
मुझे नींद आ रही है || I am sleepy. || नींद = sleepy
मुझे टॉयलेट जाना है/मुझे बाथरूम जाना है || I need to go to the toilet. || जाना है = need to go
क्या मैं टॉयलेट जा सकता हूँ/क्या मैं टॉयलेट जा सकती हूँ || May I go to the toilet? || जा सकता हूँ = may I go
मुझे दूध चाहिए || I want milk. || दूध = milk
मुझे खाना चाहिए || I want food. || खाना = food
मुझे और खाना चाहिए || I want more food. || और = more
मेरा पेट भर गया || I am full. || पेट भर गया = I am full
मुझे चॉकलेट चाहिए || I want a chocolate. || चॉकलेट = chocolate
मुझे आइसक्रीम चाहिए || I want an ice cream. || आइसक्रीम = ice cream
मुझे सेब चाहिए || I want an apple. || सेब = apple
मुझे केला चाहिए || I want a banana. || केला = banana
मुझे ठंड लग रही है || I am feeling cold. || ठंड = cold
मुझे गर्मी लग रही है || I am feeling hot. || गर्मी = hot
मुझे चोट लग गई || I got hurt. || चोट = hurt
मेरे सिर में दर्द है || I have a headache. || सिर दर्द = headache
मेरे पेट में दर्द है || I have a stomach ache. || पेट दर्द = stomach ache
मैं बीमार हूँ || I am sick. || बीमार = sick
मैं थक गया हूँ/मैं थक गई हूँ || I am tired. || थक गया = tired
# --- Feelings ---
मैं खुश हूँ || I am happy. || खुश = happy
मैं बहुत खुश हूँ || I am very happy. || बहुत = very
मैं उदास हूँ || I am sad. || उदास = sad
मुझे गुस्सा आ रहा है || I am angry. || गुस्सा = angry
मुझे डर लग रहा है || I am scared. || डर = scared
मुझे बोरियत हो रही है/मैं बोर हो रहा हूँ/मैं बोर हो रही हूँ || I am bored. || बोर = bored
मुझे यह पसंद है || I like this. || पसंद = like
मुझे यह पसंद नहीं है || I do not like this. || पसंद नहीं = do not like
मुझे तुम पसंद हो || I like you. || पसंद = like
मैं तुमसे प्यार करता हूँ/मैं तुमसे प्यार करती हूँ || I love you. || प्यार = love
मम्मी मैं आपसे प्यार करता हूँ/मम्मी मैं आपसे प्यार करती हूँ || Mummy, I love you. || प्यार करना = love
# --- Play ---
चलो खेलते हैं || Let us play. || चलो = let us
क्या तुम मेरे साथ खेलोगे/क्या तुम मेरे साथ खेलोगी || Will you play with me? || साथ = with
मुझे खेलना है || I want to play. || खेलना = play
मैं बाहर खेलना चाहता हूँ/मैं बाहर खेलना चाहती हूँ || I want to play outside. || बाहर = outside
मुझे क्रिकेट खेलना पसंद है || I like to play cricket. || खेलना = play
मुझे फुटबॉल खेलना पसंद है || I like to play football. || फुटबॉल = football
मुझे ड्राइंग करना पसंद है || I like drawing. || ड्राइंग = drawing
मुझे गाना गाना पसंद है || I like singing. || गाना गाना = singing
मुझे डांस करना पसंद है || I like dancing. || डांस = dancing
मेरी बारी है || It is my turn. || बारी = turn
तुम्हारी बारी है || It is your turn. || तुम्हारी बारी = your turn
मैं जीत गया/मैं जीत गई || I won. || जीत गया = won
तुम जीत गए/तुम जीत गई || You won. || जीत = win
मेरी गेंद कहाँ है || Where is my ball? || कहाँ = where
मेरा खिलौना टूट गया || My toy is broken. || टूट गया = broken
क्या मैं टीवी देख सकता हूँ/क्या मैं टीवी देख सकती हूँ || Can I watch TV? || देख सकता हूँ = can I watch
क्या मैं पार्क जा सकता हूँ/क्या मैं पार्क जा सकती हूँ || Can I go to the park? || पार्क = park
मुझे कहानी सुनाओ || Tell me a story. || कहानी = story
# --- School ---
मैं स्कूल जा रहा हूँ/मैं स्कूल जा रही हूँ || I am going to school. || जा रहा हूँ = am going
मैं स्कूल से आ गया/मैं स्कूल से आ गई || I am back from school. || आ गया = am back
मैं कक्षा दो में पढ़ता हूँ/मैं कक्षा दो में पढ़ती हूँ || I study in class two. || कक्षा = class
मेरी टीचर बहुत अच्छी हैं || My teacher is very nice. || अच्छी = nice
मेरा सबसे अच्छा दोस्त राहुल है || My best friend is Rahul. || सबसे अच्छा दोस्त = best friend
मुझे होमवर्क करना है || I have to do my homework. || होमवर्क = homework
मैंने अपना होमवर्क कर लिया || I have done my homework. || कर लिया = have done
मुझे समझ नहीं आया || I did not understand. || समझ नहीं आया = did not understand
क्या आप दोबारा बता सकते हैं/क्या आप फिर से बता सकती हैं || Can you please say it again? || दोबारा = again
क्या मैं अंदर आ सकता हूँ/क्या मैं अंदर आ सकती हूँ || May I come in? || अंदर = in
मेरी पेंसिल कहाँ है || Where is my pencil? || पेंसिल = pencil
मेरी किताब खो गई || I lost my book. || खो गई = lost
क्या मैं आपकी पेंसिल ले सकता हूँ/क्या मैं आपकी पेंसिल ले सकती हूँ || May I borrow your pencil? || ले सकता हूँ = may I borrow
आज छुट्टी है || Today is a holiday. || छुट्टी = holiday
आज मेरा टेस्ट है || I have a test today. || टेस्ट = test
मुझे पूरे नंबर मिले || I got full marks. || पूरे नंबर = full marks
मैं अंग्रेज़ी सीख रहा हूँ/मैं अंग्रेज़ी सीख रही हूँ || I am learning English. || सीख रहा हूँ = am learning
मुझे अंग्रेज़ी बोलना है || I want to speak English. || बोलना = speak
# --- Daily routine ---
मैं सुबह सात बजे उठता हूँ/मैं सुबह सात बजे उठती हूँ || I wake up at seven in the morning. || उठता हूँ = wake up
मैं दाँत साफ़ कर रहा हूँ/मैं दाँत साफ़ कर रही हूँ || I am brushing my teeth. || दाँत साफ़ करना = brush teeth
मैं नहा रहा हूँ/मैं नहा रही हूँ || I am taking a bath. || नहाना = take a bath
मैं खाना खा रहा हूँ/मैं खाना खा रही हूँ || I am eating food. || खा रहा हूँ = am eating
मैंने खाना खा लिया || I have eaten. || खा लिया = have eaten
मैं सोने जा रहा हूँ/मैं सोने जा रही हूँ || I am going to sleep. || सोना = sleep
मैं पढ़ाई कर रहा हूँ/मैं पढ़ाई कर रही हूँ || I am studying. || पढ़ाई = study
मैं हाथ धो रहा हूँ/मैं हाथ धो रही हूँ || I am washing my hands. || हाथ धोना = wash hands
मैं तैयार हूँ || I am ready. || तैयार = ready
मैं तैयार नहीं हूँ || I am not ready. || तैयार नहीं = not ready
अभी कितने बजे हैं/क्या टाइम हुआ है || What time is it now? || कितने बजे = what time
आज कौन सा दिन है || What day is it today? || दिन = day
# --- Questions ---
यह क्या है || What is this? || क्या = what
वह क्या है || What is that? || वह = that
यह किसका है || Whose is this? || किसका = whose
तुम कहाँ हो || Where are you? || कहाँ = where
मम्मी कहाँ हैं || Where is Mummy? || कहाँ = where
पापा कब आएंगे || When will Papa come? || कब = when
हम कहाँ जा रहे हैं || Where are we going? || जा रहे हैं = are going
क्यों || Why? || क्यों = why
ऐसा क्यों है || Why is it like this? || क्यों = why
क्या मैं यह ले सकता हूँ/क्या मैं यह ले सकती हूँ || Can I take this? || ले सकता हूँ = can I take
क्या मैं बाहर जा सकता हूँ/क्या मैं बाहर जा सकती हूँ || Can I go outside? || बाहर = outside
इसे अंग्रेज़ी में क्या कहते हैं || What do we call this in English? || अंग्रेज़ी में = in English
# --- Home and things ---
दरवाज़ा खोलो || Open the door. || खोलो = open
दरवाज़ा बंद करो || Close the door. || बंद करो = close
लाइट जलाओ || Switch on the light. || जलाओ = switch on
लाइट बंद करो || Switch off the light. || बंद करो = switch off
पंखा चलाओ || Switch on the fan. || पंखा = fan
मुझे मेरा बैग दो || Give me my bag. || दो = give
यह मेरा है || This is mine. || मेरा = mine
यह तुम्हारा है || This is yours. || तुम्हारा = yours
बाहर बारिश हो रही है || It is raining outside. || बारिश = rain
आज बहुत गर्मी है || It is very hot today. || गर्मी = hot
आज बहुत ठंड है || It is very cold today. || ठंड = cold
आसमान में चाँद है || The moon is in the sky. || चाँद = moon
मेरे पास एक कुत्ता है || I have a dog. || कुत्ता = dog
मेरे पास एक बिल्ली है || I have a cat. || बिल्ली = cat
मुझे जानवर पसंद हैं || I like animals. || जानवर = animals
# --- Helping and sharing ---
मैं आपकी मदद कर सकता हूँ/मैं आपकी मदद कर सकती हूँ || I can help you. || मदद = help
मैं यह कर सकता हूँ/मैं यह कर सकती हूँ || I can do it. || कर सकता हूँ = can do
मैं यह नहीं कर सकता/मैं यह नहीं कर सकती || I cannot do it. || नहीं कर सकता = cannot do
चलो मिलकर बाँटते हैं || Let us share. || बाँटना = share
रुको || Wait. || रुको = wait
जल्दी करो || Hurry up. || जल्दी = hurry
धीरे बोलो || Speak slowly. || धीरे = slowly
फिर से बोलो || Say it again. || फिर से = again
मुझे पता नहीं || I do not know. || पता नहीं = do not know
मुझे पता है || I know. || पता है = know
हाँ || Yes. || हाँ = yes
नहीं || No. || नहीं = no
ठीक है || Okay. || ठीक है = okay
बहुत अच्छा || Very good. || बहुत अच्छा = very good
"""

def build():
    phrases = []
    for line in RAW.strip().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split("||")]
        hi = [v.strip() for v in parts[0].split("/") if v.strip()]
        en = parts[1]
        tip = parts[2] if len(parts) > 2 else ""
        phrases.append(dict(hi=hi, en=en, tip=tip))
    return dict(phrases=phrases)

if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    target = os.path.join(here, "..", "app", "src", "main", "assets", "phrasebook.json")
    data = build()
    with open(target, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("wrote", len(data["phrases"]), "phrases to", os.path.normpath(target))
