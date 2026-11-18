#!/usr/bin/env python3
"""Generates app/src/main/assets/lessons.json from the compact lesson definitions below.

Step helpers:
  R(text, hindi)                                   repeat after Tara
  P(emoji, question, answer, hindi, accepted=[])   picture talk
  Q(question, answer, hindi, accepted=[], reply)   open question ({any} = free slot)
  RP(say, answer, hindi, accepted, branches, reply) role-play turn; branches = [(keywords, reply)]
Run:  python3 tools/gen_lessons.py
"""
import json, os

def R(text, hindi):
    return dict(type="repeat", say=f"Say: {text}", answer=text, hindi=hindi)

def P(emoji, question, answer, hindi, accepted=()):
    return dict(type="picture", say=question, picture=emoji, answer=answer, accepted=list(accepted), hindi=hindi)

def Q(question, answer, hindi, accepted=(), reply=None):
    d = dict(type="question", say=question, answer=answer, accepted=list(accepted), hindi=hindi)
    if reply: d["reply"] = reply
    return d

def RP(say, answer, hindi, accepted=(), branches=(), reply=None, picture=None):
    d = dict(type="roleplay", say=say, answer=answer, accepted=list(accepted), hindi=hindi,
             branches=[dict(keywords=k, reply=r) for k, r in branches])
    if reply: d["reply"] = reply
    if picture: d["picture"] = picture
    return d

def thing(emoji, word, article, hindi_word):
    """Picture step: 'What is this?' -> 'It is a/an <word>'."""
    return P(emoji, "What is this?", f"It is {article} {word}.",
             f"यह {hindi_word} है। अंग्रेज़ी में {hindi_word} को '{word}' कहते हैं। बोलो: It is {article} {word}.",
             accepted=[f"{article} {word}", f"this is {article} {word}"])

THEMES = [
 ("family", "Me & My Family", "👨‍👩‍👧", [
  ("hello", "Hello!", "👋", "🌟", [
    R("Hello", "'Hello' का मतलब है नमस्ते। बोलो: Hello"),
    R("Good morning", "'Good morning' सुबह की नमस्ते है। बोलो: Good morning"),
    Q("What is your name? Say: My name is, and then your name.", "My name is {any}",
      "अपना नाम बताओ। बोलो: My name is ... और अपना नाम।", accepted=["i am {any}"], reply="What a lovely name!"),
    Q("How are you?", "I am fine", "'How are you?' का मतलब है तुम कैसे हो? बोलो: I am fine.",
      accepted=["i am good", "i am happy", "fine thank you", "i am ok"], reply="I am happy too!"),
    R("Nice to meet you", "'Nice to meet you' का मतलब है आपसे मिलकर खुशी हुई। बोलो: Nice to meet you"),
    R("Thank you", "'Thank you' का मतलब है धन्यवाद। बोलो: Thank you"),
    R("Goodbye", "'Goodbye' का मतलब है अलविदा। बोलो: Goodbye"),
  ]),
  ("family1", "My Family", "👪", "🏠", [
    P("👩", "Who is this?", "This is my mummy.", "यह मम्मी हैं। बोलो: This is my mummy.", accepted=["my mummy", "mummy"]),
    P("👨", "Who is this?", "This is my daddy.", "यह पापा हैं। बोलो: This is my daddy.", accepted=["my daddy", "daddy"]),
    P("👦", "Who is this?", "This is my brother.", "यह भाई है। भाई को 'brother' कहते हैं। बोलो: This is my brother.", accepted=["my brother"]),
    P("👧", "Who is this?", "This is my sister.", "यह बहन है। बहन को 'sister' कहते हैं। बोलो: This is my sister.", accepted=["my sister"]),
    P("👵", "Who is this?", "This is my grandma.", "यह दादी या नानी हैं। उन्हें 'grandma' कहते हैं। बोलो: This is my grandma.", accepted=["my grandma", "my grandmother"]),
    R("I love my family", "'I love my family' का मतलब है मुझे अपने परिवार से प्यार है। बोलो: I love my family"),
  ]),
  ("body", "My Body", "🖐️", "💪", [
    P("👁️", "What is this?", "This is my eye.", "यह आँख है। आँख को 'eye' कहते हैं। बोलो: This is my eye.", accepted=["my eye", "eye"]),
    P("👃", "What is this?", "This is my nose.", "यह नाक है। नाक को 'nose' कहते हैं। बोलो: This is my nose.", accepted=["my nose", "nose"]),
    P("👂", "What is this?", "This is my ear.", "यह कान है। कान को 'ear' कहते हैं। बोलो: This is my ear.", accepted=["my ear", "ear"]),
    P("✋", "What is this?", "This is my hand.", "यह हाथ है। हाथ को 'hand' कहते हैं। बोलो: This is my hand.", accepted=["my hand", "hand"]),
    P("🦶", "What is this?", "This is my foot.", "यह पैर है। पैर को 'foot' कहते हैं। बोलो: This is my foot.", accepted=["my foot", "foot"]),
    R("I have two hands", "'I have two hands' का मतलब है मेरे दो हाथ हैं। बोलो: I have two hands"),
    R("I can clap my hands", "'clap' का मतलब है ताली बजाना। बोलो: I can clap my hands"),
  ]),
  ("aboutme", "All About Me", "🧒", "🎈", [
    Q("How old are you? Say: I am, and then your age.", "I am {any} years old",
      "तुम कितने साल के हो? बोलो: I am ... years old.", accepted=["i am {any}"], reply="Wow, you are growing big!"),
    Q("Are you a boy or a girl?", "I am a {any}", "बोलो: I am a boy, या I am a girl.", accepted=["boy", "girl"], reply="Super!"),
    Q("Where do you live? Say: I live in, and your city.", "I live in {any}",
      "तुम कहाँ रहते हो? बोलो: I live in ... और अपने शहर का नाम।", reply="That is a nice place!"),
    R("I like to play", "'I like to play' का मतलब है मुझे खेलना पसंद है। बोलो: I like to play"),
    R("I am happy today", "'I am happy today' का मतलब है मैं आज खुश हूँ। बोलो: I am happy today"),
  ]),
 ]),
 ("animals", "Animals", "🐾", [
  ("pets", "Pets", "🐶", "🐱", [
    thing("🐶", "dog", "a", "कुत्ता"), thing("🐱", "cat", "a", "बिल्ली"), thing("🐟", "fish", "a", "मछली"),
    thing("🐰", "rabbit", "a", "खरगोश"), thing("🦜", "parrot", "a", "तोता"),
    R("The dog says woof", "कुत्ता 'woof' बोलता है। बोलो: The dog says woof"),
    R("The cat says meow", "बिल्ली 'meow' बोलती है। बोलो: The cat says meow"),
  ]),
  ("farm", "On the Farm", "🐄", "🚜", [
    thing("🐄", "cow", "a", "गाय"), thing("🐐", "goat", "a", "बकरी"), thing("🐔", "hen", "a", "मुर्गी"),
    thing("🐴", "horse", "a", "घोड़ा"), thing("🦆", "duck", "a", "बत्तख"),
    R("The cow gives milk", "'The cow gives milk' का मतलब है गाय दूध देती है। बोलो: The cow gives milk"),
  ]),
  ("jungle", "In the Jungle", "🦁", "🌴", [
    thing("🐘", "elephant", "an", "हाथी"), thing("🦁", "lion", "a", "शेर"), thing("🐒", "monkey", "a", "बंदर"),
    thing("🐯", "tiger", "a", "बाघ"), thing("🦒", "giraffe", "a", "जिराफ़"),
    P("🐘", "Is the elephant big or small?", "The elephant is big.", "हाथी बड़ा है। बड़ा = big. बोलो: The elephant is big.",
      accepted=["it is big", "big"]),
    R("The monkey can climb", "'climb' का मतलब है चढ़ना। बोलो: The monkey can climb"),
  ]),
  ("doing", "What Can Animals Do?", "🐦", "🦋", [
    P("🐦", "What can a bird do?", "A bird can fly.", "चिड़िया उड़ सकती है। उड़ना = fly. बोलो: A bird can fly.", accepted=["it can fly", "fly"]),
    P("🐟", "What can a fish do?", "A fish can swim.", "मछली तैर सकती है। तैरना = swim. बोलो: A fish can swim.", accepted=["it can swim", "swim"]),
    P("🐸", "What can a frog do?", "A frog can jump.", "मेंढक कूद सकता है। कूदना = jump. बोलो: A frog can jump.", accepted=["it can jump", "jump"]),
    P("🐕", "What can a dog do?", "A dog can run.", "कुत्ता दौड़ सकता है। दौड़ना = run. बोलो: A dog can run.", accepted=["it can run", "run"]),
    Q("What is your favourite animal?", "My favourite animal is the {any}",
      "तुम्हारा पसंदीदा जानवर कौन सा है? बोलो: My favourite animal is the ...", accepted=["i like {any}", "my favourite animal is {any}"],
      reply="Great choice!"),
    R("I can jump like a frog", "'I can jump like a frog' का मतलब है मैं मेंढक की तरह कूद सकता हूँ। बोलो: I can jump like a frog"),
  ]),
 ]),
 ("food", "Food", "🍎", [
  ("fruits", "Fruits", "🍎", "🍓", [
    thing("🍎", "apple", "an", "सेब"), thing("🍌", "banana", "a", "केला"), thing("🥭", "mango", "a", "आम"),
    P("🍇", "What are these?", "These are grapes.", "ये अंगूर हैं। अंगूर = grapes. बोलो: These are grapes.", accepted=["grapes"]),
    thing("🍊", "orange", "an", "संतरा"),
    R("I like mangoes", "'I like mangoes' का मतलब है मुझे आम पसंद हैं। बोलो: I like mangoes"),
  ]),
  ("veggies", "Vegetables", "🥕", "🥕", [
    thing("🥕", "carrot", "a", "गाजर"), thing("🍅", "tomato", "a", "टमाटर"), thing("🥔", "potato", "a", "आलू"),
    thing("🧅", "onion", "an", "प्याज़"),
    P("🌽", "What is this?", "It is corn.", "यह भुट्टा है। भुट्टे को 'corn' कहते हैं। बोलो: It is corn.", accepted=["corn"]),
    R("Vegetables make me strong", "'strong' का मतलब है ताकतवर। बोलो: Vegetables make me strong"),
  ]),
  ("meals", "Meal Time", "🍽️", "🍛", [
    P("🥛", "What is this?", "It is milk.", "यह दूध है। दूध = milk. बोलो: It is milk.", accepted=["milk", "a glass of milk"]),
    P("🍚", "What is this?", "It is rice.", "यह चावल है। चावल = rice. बोलो: It is rice.", accepted=["rice"]),
    P("🍞", "What is this?", "It is bread.", "यह ब्रेड है। बोलो: It is bread.", accepted=["bread"]),
    Q("What do you eat for breakfast? Say: I eat, and your food.", "I eat {any} for breakfast",
      "नाश्ते में क्या खाते हो? बोलो: I eat ... for breakfast.", accepted=["i eat {any}"], reply="Yummy!"),
    R("I am hungry", "'I am hungry' का मतलब है मुझे भूख लगी है। बोलो: I am hungry"),
    R("I am thirsty", "'I am thirsty' का मतलब है मुझे प्यास लगी है। बोलो: I am thirsty"),
    R("May I have some water please", "पानी माँगने के लिए बोलो: May I have some water please"),
  ]),
  ("shop", "At the Fruit Shop", "🛒", "🧺", [
    RP("Hello! Welcome to my fruit shop. Say: Hello!", "Hello", "दुकानदार को नमस्ते करो। बोलो: Hello",
       accepted=["hi", "good morning"], reply="Hello, little friend!", picture="🧑‍🌾"),
    RP("What do you want to buy?", "I want a mango", "तुम्हें क्या चाहिए? बोलो: I want a mango, या I want a banana.",
       accepted=["i want {any}", "a {any} please"],
       branches=[(["mango", "mangoes"], "Here is a sweet mango!"), (["banana", "bananas"], "Here is a yellow banana!"),
                 (["apple", "apples"], "Here is a red apple!")], reply="Here you go!", picture="🧺"),
    RP("How many do you want?", "I want two", "कितने चाहिए? बोलो: I want two.", accepted=["{any} please", "two", "one", "three"],
       reply="Okay!", picture="🔢"),
    RP("That is ten rupees, please.", "Here you are", "पैसे देते समय बोलो: Here you are", accepted=["here it is"],
       reply="Thank you!", picture="💰"),
    RP("Thank you! Have a nice day.", "Thank you. Bye", "धन्यवाद कहो और अलविदा बोलो: Thank you. Bye",
       accepted=["thank you", "bye", "goodbye"], reply="Bye bye!", picture="👋"),
    R("I bought fruits at the shop", "'bought' का मतलब है खरीदा। बोलो: I bought fruits at the shop"),
  ]),
 ]),
 ("colours", "Colours & Shapes", "🌈", [
  ("colours1", "Colours", "🎨", "🖍️", [
    P("🔴", "What colour is this?", "It is red.", "यह लाल है। लाल = red. बोलो: It is red.", accepted=["red"]),
    P("🔵", "What colour is this?", "It is blue.", "यह नीला है। नीला = blue. बोलो: It is blue.", accepted=["blue"]),
    P("🟢", "What colour is this?", "It is green.", "यह हरा है। हरा = green. बोलो: It is green.", accepted=["green"]),
    P("🟡", "What colour is this?", "It is yellow.", "यह पीला है। पीला = yellow. बोलो: It is yellow.", accepted=["yellow"]),
    P("🟠", "What colour is this?", "It is orange.", "यह नारंगी है। नारंगी = orange. बोलो: It is orange.", accepted=["orange"]),
    Q("What is your favourite colour?", "My favourite colour is {any}",
      "तुम्हारा पसंदीदा रंग कौन सा है? बोलो: My favourite colour is ...", accepted=["i like {any}"], reply="That is a beautiful colour!"),
  ]),
  ("colours2", "Colourful Things", "🍓", "🌈", [
    P("🍓", "What colour is the strawberry?", "The strawberry is red.", "स्ट्रॉबेरी लाल है। बोलो: The strawberry is red.", accepted=["it is red", "red"]),
    P("🍌", "What colour is the banana?", "The banana is yellow.", "केला पीला है। बोलो: The banana is yellow.", accepted=["it is yellow", "yellow"]),
    P("🌳", "What colour is the tree?", "The tree is green.", "पेड़ हरा है। बोलो: The tree is green.", accepted=["it is green", "green"]),
    P("🌊", "What colour is the sea?", "The sea is blue.", "समुद्र नीला है। बोलो: The sea is blue.", accepted=["it is blue", "blue"]),
    P("🐘", "What colour is the elephant?", "The elephant is grey.", "हाथी स्लेटी रंग का है। स्लेटी = grey. बोलो: The elephant is grey.",
      accepted=["it is grey", "grey"]),
    R("I can see a rainbow", "'rainbow' का मतलब है इंद्रधनुष। बोलो: I can see a rainbow"),
  ]),
  ("shapes", "Shapes", "🔺", "🔷", [
    P("⚪", "What shape is this?", "It is a circle.", "यह गोला है। गोला = circle. बोलो: It is a circle.", accepted=["a circle", "circle"]),
    P("🟥", "What shape is this?", "It is a square.", "यह वर्ग है। वर्ग = square. बोलो: It is a square.", accepted=["a square", "square"]),
    P("🔺", "What shape is this?", "It is a triangle.", "यह त्रिभुज है। त्रिभुज = triangle. बोलो: It is a triangle.", accepted=["a triangle", "triangle"]),
    P("⭐", "What shape is this?", "It is a star.", "यह तारा है। तारा = star. बोलो: It is a star.", accepted=["a star", "star"]),
    P("❤️", "What shape is this?", "It is a heart.", "यह दिल है। दिल = heart. बोलो: It is a heart.", accepted=["a heart", "heart"]),
    R("A ball is round", "'round' का मतलब है गोल। बोलो: A ball is round"),
  ]),
  ("counting", "Let's Count", "🔢", "🎲", [
    R("One two three four five", "गिनती बोलो: One two three four five"),
    P("🍎🍎", "How many apples?", "Two apples.", "दो सेब हैं। दो = two. बोलो: Two apples.", accepted=["two", "there are two apples"]),
    P("⭐⭐⭐", "How many stars?", "Three stars.", "तीन तारे हैं। तीन = three. बोलो: Three stars.", accepted=["three", "there are three stars"]),
    P("🐟🐟🐟🐟", "How many fish?", "Four fish.", "चार मछलियाँ हैं। चार = four. बोलो: Four fish.", accepted=["four", "there are four fish"]),
    P("🎈🎈🎈🎈🎈", "How many balloons?", "Five balloons.", "पाँच गुब्बारे हैं। पाँच = five. बोलो: Five balloons.", accepted=["five", "there are five balloons"]),
    R("Six seven eight nine ten", "गिनती बोलो: Six seven eight nine ten"),
  ]),
 ]),
 ("school", "At School", "🏫", [
  ("classroom", "My Classroom", "✏️", "🎒", [
    thing("📕", "book", "a", "किताब"), thing("✏️", "pencil", "a", "पेंसिल"), thing("🎒", "bag", "a", "बस्ता"),
    P("🖍️", "What are these?", "These are crayons.", "ये क्रेयॉन हैं। बोलो: These are crayons.", accepted=["crayons"]),
    P("🪑", "What is this?", "It is a chair.", "यह कुर्सी है। कुर्सी = chair. बोलो: It is a chair.", accepted=["a chair", "this is a chair"]),
    R("I go to school", "'I go to school' का मतलब है मैं स्कूल जाता हूँ। बोलो: I go to school"),
  ]),
  ("teacher", "Talking to My Teacher", "👩‍🏫", "🍎", [
    R("Good morning, teacher", "टीचर को सुबह नमस्ते करो। बोलो: Good morning, teacher"),
    R("May I come in", "कक्षा में आने की अनुमति के लिए बोलो: May I come in"),
    R("May I go to the toilet", "टॉयलेट जाने की अनुमति के लिए बोलो: May I go to the toilet"),
    R("I do not understand", "जब समझ न आए तो बोलो: I do not understand"),
    R("Can you help me please", "मदद माँगने के लिए बोलो: Can you help me please"),
    R("Thank you, teacher", "टीचर को धन्यवाद कहो। बोलो: Thank you, teacher"),
    RP("Good morning! How are you today?", "I am fine, thank you", "टीचर को जवाब दो। बोलो: I am fine, thank you",
       accepted=["i am fine", "i am good", "fine thank you"], reply="Wonderful! Let us start the class.", picture="👩‍🏫"),
  ]),
  ("friends", "Making Friends", "🧑‍🤝‍🧑", "🤝", [
    RP("Hi! I am Tara. What is your name?", "My name is {any}", "अपना नाम बताओ। बोलो: My name is ...",
       accepted=["i am {any}"], reply="Nice to meet you!", picture="🦜"),
    RP("Do you want to play with me?", "Yes, let us play", "खेलने के लिए हाँ कहो। बोलो: Yes, let us play",
       accepted=["yes", "ok", "yes please"], reply="Yay! Let us play!", picture="⚽"),
    RP("What game do you like?", "I like {any}", "कौन सा खेल पसंद है? बोलो: I like ...",
       branches=[(["football", "ball"], "Football is fun!"), (["hide", "seek"], "Hide and seek is my favourite!"),
                 (["cricket"], "Cricket! Let us play!")], reply="That sounds fun!", picture="🎮"),
    R("You are my friend", "'You are my friend' का मतलब है तुम मेरे दोस्त हो। बोलो: You are my friend"),
    R("Let us share", "'share' का मतलब है मिल-बाँटकर। बोलो: Let us share"),
  ]),
  ("play", "Playtime", "⚽", "🏆", [
    thing("⚽", "ball", "a", "गेंद"),
    P("🪁", "What is this?", "It is a kite.", "यह पतंग है। पतंग = kite. बोलो: It is a kite.", accepted=["a kite", "kite"]),
    P("🏃", "What is he doing?", "He is running.", "वह दौड़ रहा है। बोलो: He is running.", accepted=["running", "he is run"]),
    P("🧒", "What is she doing? 🎵", "She is singing.", "वह गा रही है। गाना = sing. बोलो: She is singing.", accepted=["singing"]),
    P("💃", "What is she doing?", "She is dancing.", "वह नाच रही है। नाचना = dance. बोलो: She is dancing.", accepted=["dancing"]),
    R("I like to play outside", "'outside' का मतलब है बाहर। बोलो: I like to play outside"),
  ]),
 ]),
 ("feelings", "Feelings & My Day", "😊", [
  ("feelings1", "How Do I Feel?", "😊", "😊", [
    P("😊", "How does she feel?", "She is happy.", "वह खुश है। खुश = happy. बोलो: She is happy.", accepted=["happy"]),
    P("😢", "How does he feel?", "He is sad.", "वह उदास है। उदास = sad. बोलो: He is sad.", accepted=["sad"]),
    P("😠", "How does he feel?", "He is angry.", "वह गुस्से में है। गुस्सा = angry. बोलो: He is angry.", accepted=["angry"]),
    P("😴", "How does she feel?", "She is sleepy.", "उसे नींद आ रही है। नींद = sleepy. बोलो: She is sleepy.", accepted=["sleepy", "tired"]),
    P("😨", "How does he feel?", "He is scared.", "वह डरा हुआ है। डरा = scared. बोलो: He is scared.", accepted=["scared", "afraid"]),
    Q("How do you feel today?", "I feel {any}", "आज तुम कैसा महसूस कर रहे हो? बोलो: I feel happy, या I feel sleepy.",
      accepted=["i am {any}"], reply="Thank you for telling me!"),
  ]),
  ("routine", "My Day", "⏰", "☀️", [
    P("⏰", "What do you do in the morning?", "I wake up.", "सुबह मैं उठता हूँ। उठना = wake up. बोलो: I wake up.", accepted=["wake up"]),
    P("🪥", "What do you do next?", "I brush my teeth.", "मैं दाँत साफ़ करता हूँ। बोलो: I brush my teeth.", accepted=["brush my teeth", "brush teeth"]),
    P("🛁", "What do you do next?", "I take a bath.", "मैं नहाता हूँ। बोलो: I take a bath.", accepted=["take a bath", "bath"]),
    P("🚌", "Then what do you do?", "I go to school.", "मैं स्कूल जाता हूँ। बोलो: I go to school.", accepted=["go to school", "school"]),
    P("🛏️", "What do you do at night?", "I go to sleep.", "रात को मैं सोता हूँ। बोलो: I go to sleep.", accepted=["sleep", "i sleep"]),
    R("Good night", "'Good night' का मतलब है शुभ रात्रि। बोलो: Good night"),
  ]),
  ("weather", "Weather", "🌦️", "☂️", [
    P("☀️", "How is the weather?", "It is sunny.", "धूप निकली है। धूप = sunny. बोलो: It is sunny.", accepted=["sunny"]),
    P("🌧️", "How is the weather?", "It is rainy.", "बारिश हो रही है। बारिश = rainy. बोलो: It is rainy.", accepted=["rainy", "it is raining"]),
    P("☁️", "How is the weather?", "It is cloudy.", "बादल हैं। बादल = cloudy. बोलो: It is cloudy.", accepted=["cloudy"]),
    P("🥶", "How is the weather?", "It is cold.", "ठंड है। ठंड = cold. बोलो: It is cold.", accepted=["cold"]),
    P("🥵", "How is the weather?", "It is hot.", "गर्मी है। गर्मी = hot. बोलो: It is hot.", accepted=["hot"]),
    R("I need an umbrella", "'umbrella' का मतलब है छाता। बोलो: I need an umbrella"),
  ]),
  ("manners", "Magic Words", "✨", "🏅", [
    R("Please", "कुछ माँगते समय 'please' बोलते हैं। बोलो: Please"),
    R("Thank you very much", "धन्यवाद कहने के लिए बोलो: Thank you very much"),
    R("Sorry", "गलती होने पर 'sorry' बोलते हैं। बोलो: Sorry"),
    R("Excuse me", "किसी का ध्यान खींचने के लिए बोलो: Excuse me"),
    RP("Here is a cookie for you! What do you say?", "Thank you", "कुछ मिलने पर बोलो: Thank you",
       accepted=["thanks", "thank you very much"], reply="You are welcome!", picture="🍪"),
    RP("Oops! You bumped into me. What do you say?", "Sorry", "टकराने पर बोलो: Sorry", accepted=["i am sorry"],
       reply="That is okay!", picture="🙈"),
  ]),
 ]),
]

def build():
    themes = []
    for tid, ttitle, temoji, lessons in THEMES:
        out = []
        for lid, ltitle, lemoji, sticker, steps in lessons:
            for i, s in enumerate(steps, 1):
                s["id"] = f"{lid}-{i}"
            out.append(dict(id=lid, title=ltitle, emoji=lemoji, sticker=sticker, steps=steps))
        themes.append(dict(id=tid, title=ttitle, emoji=temoji, lessons=out))
    return dict(themes=themes)

if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    target = os.path.join(here, "..", "app", "src", "main", "assets", "lessons.json")
    with open(target, "w", encoding="utf-8") as f:
        json.dump(build(), f, ensure_ascii=False, indent=1)
    print("wrote", os.path.normpath(target))
