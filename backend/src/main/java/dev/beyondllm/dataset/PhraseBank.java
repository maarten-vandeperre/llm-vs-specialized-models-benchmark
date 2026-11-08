package dev.beyondllm.dataset;

import java.util.List;
import java.util.Map;

/**
 * Aligned phrase templates: every entry is {en, nl, fr} with identical meaning, so multilingual records are
 * true equivalents. Placeholders: {p} product (singular), {o} another product, {id} order id.
 * All text is fictional; no real customer data.
 */
final class PhraseBank {

    private PhraseBank() {
    }

    static String[] t(String en, String nl, String fr) {
        return new String[]{en, nl, fr};
    }

    static int idx(String lang) {
        return switch (lang) {
            case "en" -> 0;
            case "nl" -> 1;
            case "fr" -> 2;
            default -> throw new IllegalArgumentException(lang);
        };
    }

    // ---- complaints (explicit, implicit, negated) ------------------------------------------------------------
    record Variant(String[] text, String tag) {
    }

    static Variant v(String tag, String en, String nl, String fr) {
        return new Variant(t(en, nl, fr), tag);
    }

    static final Map<String, List<Variant>> COMPLAINTS = Map.of(
            "temperature", List.of(
                    v("explicit", "My {p} arrived cold.", "Mijn {p} kwam koud aan.", "Mon {p} est arrivé froid."),
                    v("explicit", "The {p} was lukewarm when I got it.", "De {p} was lauw toen ik hem kreeg.", "Le {p} était tiède quand je l'ai reçu."),
                    v("negation", "My {p} was not hot at all.", "Mijn {p} was helemaal niet warm.", "Mon {p} n'était pas chaud du tout."),
                    v("implicit", "I could have served my {p} as an iced drink, and I did not order it iced.",
                            "Ik had mijn {p} als ijsdrank kunnen serveren, en ik had hem niet met ijs besteld.",
                            "J'aurais pu servir mon {p} comme boisson glacée, et je ne l'avais pas commandé glacé.")),
            "taste", List.of(
                    v("explicit", "The {p} tasted burnt.", "De {p} smaakte verbrand.", "Le {p} avait un goût de brûlé."),
                    v("explicit", "My {p} tasted sour and strange.", "Mijn {p} smaakte zuur en vreemd.", "Mon {p} avait un goût aigre et bizarre."),
                    v("implicit", "After one sip of the {p} I poured the rest down the sink.",
                            "Na één slok van de {p} heb ik de rest door de gootsteen gegoten.",
                            "Après une gorgée du {p}, j'ai versé le reste dans l'évier.")),
            "wrong_item", List.of(
                    v("explicit", "I received a {o} instead of my {p}.", "Ik kreeg een {o} in plaats van mijn {p}.", "J'ai reçu un {o} au lieu de mon {p}."),
                    v("explicit", "You sent me the wrong item: a {o}, not the {p} I ordered.",
                            "Jullie stuurden het verkeerde product: een {o}, niet de {p} die ik bestelde.",
                            "Vous m'avez envoyé le mauvais article : un {o}, pas le {p} que j'avais commandé.")),
            "missing_item", List.of(
                    v("explicit", "The {p} was missing from my order.", "De {p} ontbrak in mijn bestelling.", "Le {p} manquait dans ma commande."),
                    v("implicit", "I opened the bag and there was no {p} in it.", "Ik opende de zak en er zat geen {p} in.", "J'ai ouvert le sac et il n'y avait pas de {p} dedans.")),
            "late_delivery", List.of(
                    v("explicit", "My order arrived more than an hour late.", "Mijn bestelling kwam meer dan een uur te laat.", "Ma commande est arrivée avec plus d'une heure de retard."),
                    v("explicit", "I have been waiting forever and my order is still not here.", "Ik wacht al eeuwen en mijn bestelling is er nog steeds niet.", "J'attends depuis une éternité et ma commande n'est toujours pas là.")),
            "overcharged", List.of(
                    v("explicit", "I was charged twice for the same order.", "Er werd twee keer betaald voor dezelfde bestelling.", "J'ai été débité deux fois pour la même commande."),
                    v("explicit", "I was charged more than the price on your menu.", "Ik moest meer betalen dan de prijs op jullie menu.", "On m'a facturé plus que le prix indiqué sur votre carte.")),
            "rude_staff", List.of(
                    v("explicit", "The barista was really rude to me.", "De barista was erg onbeleefd tegen mij.", "Le barista a été vraiment impoli avec moi."),
                    v("implicit", "The person at the counter rolled their eyes and ignored my question.",
                            "De persoon aan de toonbank rolde met de ogen en negeerde mijn vraag.",
                            "La personne au comptoir a levé les yeux au ciel et a ignoré ma question.")));

    // ---- intents ------------------------------------------------------------------------------------------
    static final Map<String, List<Variant>> INTENTS = Map.of(
            "refund", List.of(
                    v("explicit", "I want a refund.", "Ik wil mijn geld terug.", "Je veux être remboursé."),
                    v("explicit", "Please refund me.", "Gelieve mij terug te betalen.", "Merci de me rembourser."),
                    v("explicit", "I would like my money back.", "Ik zou graag mijn geld terugkrijgen.", "J'aimerais récupérer mon argent."),
                    v("implicit", "I really don't think I should have to pay for this.", "Ik vind echt niet dat ik hiervoor moet betalen.", "Je ne pense vraiment pas devoir payer pour ça.")),
            "replacement", List.of(
                    v("explicit", "Please send me a new {p}.", "Stuur me alsjeblieft een nieuwe {p}.", "Merci de m'envoyer un nouveau {p}."),
                    v("explicit", "Can you replace it?", "Kunnen jullie het vervangen?", "Pouvez-vous le remplacer ?"),
                    v("negation", "I don't want a refund, I just want a new {p}.", "Ik wil geen terugbetaling, ik wil gewoon een nieuwe {p}.", "Je ne veux pas de remboursement, je veux juste un nouveau {p}.")),
            "order_status", List.of(
                    v("explicit", "Where is my order?", "Waar is mijn bestelling?", "Où est ma commande ?"),
                    v("explicit", "Can you tell me when my order will arrive?", "Kunnen jullie me zeggen wanneer mijn bestelling aankomt?", "Pouvez-vous me dire quand ma commande arrivera ?"),
                    v("implicit", "I'm still sitting here waiting and wondering what happened to my order.", "Ik zit hier nog steeds te wachten en vraag me af wat er met mijn bestelling is gebeurd.", "Je suis toujours là à attendre et je me demande ce qui est arrivé à ma commande.")),
            "cancel_order", List.of(
                    v("explicit", "Please cancel my order.", "Annuleer alsjeblieft mijn bestelling.", "Merci d'annuler ma commande."),
                    v("explicit", "I changed my mind, I don't want the order anymore, please cancel it.", "Ik ben van gedachten veranderd, ik wil de bestelling niet meer, annuleer ze alsjeblieft.", "J'ai changé d'avis, je ne veux plus la commande, annulez-la s'il vous plaît.")),
            "product_question", List.of(
                    v("explicit", "Do you have oat milk for the {p}?", "Hebben jullie havermelk voor de {p}?", "Avez-vous du lait d'avoine pour le {p} ?"),
                    v("explicit", "Is the {p} vegan?", "Is de {p} veganistisch?", "Le {p} est-il végan ?"),
                    v("explicit", "Does the {p} contain nuts?", "Bevat de {p} noten?", "Le {p} contient-il des noix ?")),
            "feedback", List.of(
                    v("explicit", "Just wanted to let you know.", "Ik wilde het jullie gewoon laten weten.", "Je voulais simplement vous le signaler."),
                    v("explicit", "No need to do anything, but you should know.", "Jullie hoeven niets te doen, maar jullie moeten het weten.", "Pas besoin de faire quoi que ce soit, mais vous devriez le savoir.")));

    static final List<Variant> CONTRADICTION_TO_REPLACEMENT = List.of(
            v("contradictory", "I want a refund. Actually, forget the refund, please just send a replacement.",
                    "Ik wil mijn geld terug. Eigenlijk, laat die terugbetaling maar, stuur gewoon een vervanging.",
                    "Je veux être remboursé. En fait, oubliez le remboursement, envoyez-moi simplement un remplacement."));
    static final List<Variant> CONTRADICTION_TO_REFUND = List.of(
            v("contradictory", "Please send me a new one. No wait, I'd rather have my money back.",
                    "Stuur me alsjeblieft een nieuwe. Nee wacht, ik heb liever mijn geld terug.",
                    "Envoyez-moi un nouveau s'il vous plaît. Non attendez, je préfère être remboursé."));

    static final List<Variant> PRAISE = List.of(
            v("explicit", "The {p} was amazing, thank you!", "De {p} was fantastisch, bedankt!", "Le {p} était incroyable, merci !"),
            v("explicit", "Best {p} I have had in years.", "De beste {p} die ik in jaren heb gehad.", "Le meilleur {p} que j'aie bu depuis des années."),
            v("negation", "The {p} was not bad at all, actually really good.", "De {p} was helemaal niet slecht, eigenlijk heel goed.", "Le {p} n'était pas mauvais du tout, en fait vraiment bon."),
            v("explicit", "Your staff were lovely and the {p} was perfect.", "Jullie personeel was heel vriendelijk en de {p} was perfect.", "Votre personnel était adorable et le {p} était parfait."));

    static final List<Variant> AMBIGUOUS = List.of(
            v("ambiguous", "The {p} wasn't bad, although I'm not sure I would order it again.",
                    "De {p} was niet slecht, al weet ik niet zeker of ik hem opnieuw zou bestellen.",
                    "Le {p} n'était pas mauvais, même si je ne suis pas sûr de le recommander."),
            v("ambiguous", "Hmm, that was an interesting experience at your shop.", "Hmm, dat was een interessante ervaring in jullie zaak.", "Hmm, c'était une expérience intéressante dans votre café."),
            v("ambiguous", "I have mixed feelings about yesterday.", "Ik heb gemengde gevoelens over gisteren.", "J'ai des sentiments mitigés à propos d'hier."),
            v("ambiguous", "Not sure what to think about the new blend.", "Ik weet niet goed wat ik van de nieuwe melange moet denken.", "Je ne sais pas trop quoi penser du nouveau mélange."),
            v("ambiguous", "Well. The {p}. Yes. It was a {p}.", "Tja. De {p}. Ja. Het was een {p}.", "Bon. Le {p}. Oui. C'était un {p}."),
            v("ambiguous", "Some things were fine, some things were less fine.", "Sommige dingen waren goed, andere minder.", "Certaines choses étaient bien, d'autres moins."),
            v("ambiguous", "I'll have to think about it before I come back.", "Ik moet erover nadenken voor ik terugkom.", "Je dois y réfléchir avant de revenir."),
            v("ambiguous", "So that happened with my {p} today.", "Dat gebeurde dus vandaag met mijn {p}.", "Voilà ce qui est arrivé à mon {p} aujourd'hui."));

    // ---- extraction sentences ----------------------------------------------------------------------------
    static final List<String[]> ORDER_WITH_ID = List.of(
            t("Order {id} contains {items}.", "Bestelling {id} bevat {items}.", "La commande {id} contient {items}."),
            t("My order {id} had {items}.", "Mijn bestelling {id} had {items}.", "Ma commande {id} comprenait {items}."),
            t("I ordered {items} (order {id}).", "Ik bestelde {items} (bestelling {id}).", "J'ai commandé {items} (commande {id})."),
            t("For order number {id} I got {items}.", "Voor bestelnummer {id} kreeg ik {items}.", "Pour la commande numéro {id}, j'ai reçu {items}."));
    static final List<String[]> ORDER_NO_ID = List.of(
            t("I ordered {items}.", "Ik bestelde {items}.", "J'ai commandé {items}."),
            t("My order had {items}.", "Mijn bestelling had {items}.", "Ma commande comprenait {items}."));
    static final List<String[]> ID_ONLY = List.of(
            t("This is about order {id}.", "Het gaat over bestelling {id}.", "Il s'agit de la commande {id}."),
            t("Order reference: {id}.", "Bestelreferentie: {id}.", "Référence de commande : {id}."),
            t("(order {id})", "(bestelling {id})", "(commande {id})"));
    static final List<String[]> TOTAL = List.of(
            t("The total was {money}.", "Het totaal was {money}.", "Le total était de {money}."),
            t("I paid {money}.", "Ik betaalde {money}.", "J'ai payé {money}."),
            t("It cost me {money} in total.", "Het kostte me in totaal {money}.", "Cela m'a coûté {money} au total."));
    static final List<String[]> NUMERIC_DISTRACTORS = List.of(
            t("We were sitting at table 7.", "We zaten aan tafel 7.", "Nous étions assis à la table 7."),
            t("It took 15 minutes before anyone noticed me.", "Het duurde 15 minuten voor iemand me opmerkte.", "Il a fallu 15 minutes avant que quelqu'un me remarque."),
            t("I live at number 42, the blue door.", "Ik woon op nummer 42, de blauwe deur.", "J'habite au numéro 42, la porte bleue."));
    static final List<String[]> PRODUCT_DISTRACTORS = List.of(
            t("I usually take a {o}, but not this time.", "Normaal neem ik een {o}, maar deze keer niet.", "D'habitude je prends un {o}, mais pas cette fois."),
            t("My friend said your {o} is great, maybe next time.", "Mijn vriend zei dat jullie {o} geweldig is, misschien volgende keer.", "Mon ami dit que votre {o} est excellent, peut-être la prochaine fois."));

    static final List<String[]> FILLER = List.of(
            t("It has been raining all week here.", "Het heeft hier de hele week geregend.", "Il a plu toute la semaine ici."),
            t("I was on my way to work when I stopped by.", "Ik was op weg naar mijn werk toen ik langskwam.", "J'étais en route pour le travail quand je suis passé."),
            t("My neighbour recommended your shop last month.", "Mijn buurvrouw raadde jullie zaak vorige maand aan.", "Ma voisine m'a recommandé votre café le mois dernier."),
            t("The music in the shop was nice, by the way.", "De muziek in de zaak was trouwens leuk.", "La musique dans le café était sympa, au passage."),
            t("I am writing this from the train.", "Ik schrijf dit vanuit de trein.", "J'écris ceci depuis le train."),
            t("Hello there.", "Hallo.", "Bonjour."),
            t("I hope you are having a good day.", "Ik hoop dat jullie een goede dag hebben.", "J'espère que vous passez une bonne journée."),
            t("Sorry for the long message.", "Sorry voor het lange bericht.", "Désolé pour ce long message."));

    /** Long, irrelevant English filler for the input-length scenario (no numbers, no product names). */
    static final List<String> LONG_FILLER = List.of(
            "Our community notice board has been updated with information about the neighbourhood clean-up day.",
            "The city council announced that the park benches near the river will be repainted in spring.",
            "Many visitors told us they enjoy reading the local newspaper while sitting near the window.",
            "The weather forecast promises a mix of sunshine and showers for the coming weekend.",
            "A small jazz band will rehearse in the community hall on Thursday evenings.",
            "The library across the street extended its opening hours for students preparing for exams.",
            "Several cyclists asked whether there will be more bicycle racks on the square.",
            "Our staff attended a workshop about sustainable packaging and waste reduction.",
            "The bakery down the road is renovating its storefront and will reopen soon.",
            "Children from the nearby school painted a colourful mural on the side wall.",
            "Some guests mentioned that the plants in the corner make the room feel calmer.",
            "The postal service changed its delivery schedule for parcels in this district.",
            "We received a lovely postcard from a regular guest who is travelling abroad.",
            "There is a new exhibition of local photography at the cultural centre.",
            "Road works on the main avenue may cause some delays for buses during the morning.",
            "A volunteer group is collecting warm clothes for the winter shelter.",
            "The farmers market moved to the other side of the square for the season.",
            "Our opening hours on public holidays are listed on the door.",
            "A local author will read from her new novel at the bookshop next door.",
            "The street lights were replaced with energy efficient lamps last autumn.");
}
