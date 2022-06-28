package org.igor.klimov.app.search.snippetParser;

public interface SnippetParser {

    String create();

    //    private Document clearContentFromExtraElements(String content) {
//        long start = System.currentTimeMillis();
//        Document document = Jsoup.parse(content, "UTF-8");
//        document.getElementsByTag("header").remove();
//        document.getElementsByTag("noscript").remove();
//        document.getElementsByTag("footer").remove();
//        document.getElementsByTag("script").remove();
//
//        System.out.println("\t\tclearContentFromExtraElements invocation took " + (System.currentTimeMillis() - start));
//
//        return document;
//    }

//    private List<String> getListOfPageTextElements(String content) {
//        Document clearedContent = clearContentFromExtraElements(content);
//        List<String> elementsOwnText = clearedContent.getAllElements()
//                .stream()
//                .filter(Element::hasText)
//                .map(e -> e.ownText())
//                .filter(e -> !e.isEmpty())
//                .collect(Collectors.toList());
//        return elementsOwnText;
//    }



//    private List<WordOnPage> getWordsOnPageList(){
//        String html = page.getContent();
//        String body = Jsoup.parse(html).body().wholeText();
//
//        String[] wordsFromPage = body.split("[^A-zА-я\\-]");
//        Map<String, String> basicForms = counter.getBasicForms(wordsFromPage);
//
//
//        List<WordOnPage> wordsOnPage = new ArrayList<>();
//        StringBuilder builder = new StringBuilder(body);
//        for(Map.Entry<String, String> e : basicForms.entrySet()){
//            WordOnPage wop = new WordOnPage();
//            wop.setWord(e.getValue());
//            wop.setLemma(e.getKey());
//            wop.setPosition(body.indexOf(e.getValue()));
//            builder.delete(wop.getPosition(), wop.getWord().length());
//            body = builder.toString();
//            wordsOnPage.add(wop);
//        }
//
//        return wordsOnPage;
//    }
}
