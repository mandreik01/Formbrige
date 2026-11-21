package pl.formbridge.core.model;

import java.util.ArrayList;
import java.util.List;

public final class FormSpec {

    public final String title;
    public final List<PageSpec> pages = new ArrayList<>();
    public final List<String> proposedDictionaries = new ArrayList<>();

    public FormSpec(String title) {
        this.title = title;
    }

    public FormSpec add(PageSpec p) {
        pages.add(p);
        return this;
    }
}
