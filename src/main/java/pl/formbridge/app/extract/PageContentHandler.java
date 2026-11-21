package pl.formbridge.app.extract;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class PageContentHandler extends DefaultHandler {

    private final List<String> pages = new ArrayList<>();
    private final StringBuilder page = new StringBuilder();
    private final StringBuilder cell = new StringBuilder();

    private int pageDepth = -1;
    private int depth;
    private boolean inTable;
    private boolean inCell;
    private boolean rowStarted;
    private boolean cellStarted;

    List<String> pages() {
        List<String> all = new ArrayList<>(pages);
        String last = page.toString().strip();
        if (!last.isEmpty()) {
            all.add(last);
        }
        return all.stream().filter(p -> !p.isBlank()).toList();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        depth++;
        String name = name(localName, qName);
        if ("div".equals(name) && isPageMarker(attributes.getValue("class"))) {
            startPage();
            pageDepth = depth;
            return;
        }
        switch (name) {
            case "table" -> {
                if (pageDepth < 0 && !inTable) {
                    startPage();
                }
                inTable = true;
            }
            case "tr" -> rowStarted = false;
            case "td", "th" -> {
                inCell = true;
                cell.setLength(0);
            }
            case "p", "br", "h1", "h2", "h3", "li" -> {
                if (!inCell) {
                    newLine();
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        String name = name(localName, qName);
        switch (name) {
            case "td", "th" -> {
                if (inCell) {
                    if (rowStarted) {
                        page.append('\t');
                    }
                    page.append(cell.toString().strip().replace('\n', ' ').strip());
                    rowStarted = true;
                    cellStarted = true;
                    inCell = false;
                }
            }
            case "tr" -> {
                if (cellStarted) {
                    newLine();
                    cellStarted = false;
                }
                rowStarted = false;
            }
            case "table" -> inTable = false;
            case "p", "h1", "h2", "h3", "li" -> {
                if (!inCell) {
                    newLine();
                }
            }
            default -> {
            }
        }
        if (depth == pageDepth) {
            pageDepth = -1;
            flushPage();
        }
        depth--;
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        (inCell ? cell : page).append(ch, start, length);
    }

    private static boolean isPageMarker(String cssClass) {
        return "page".equals(cssClass) || "slide-content".equals(cssClass) || "sheet".equals(cssClass);
    }

    private void startPage() {
        flushPage();
    }

    private void flushPage() {
        String text = page.toString().strip();
        if (!text.isEmpty()) {
            pages.add(text);
        }
        page.setLength(0);
        rowStarted = false;
        cellStarted = false;
    }

    private void newLine() {
        if (page.length() > 0 && page.charAt(page.length() - 1) != '\n') {
            page.append('\n');
        }
    }

    private static String name(String localName, String qName) {
        String n = localName == null || localName.isEmpty() ? qName : localName;
        int colon = n.indexOf(':');
        return (colon < 0 ? n : n.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }
}
