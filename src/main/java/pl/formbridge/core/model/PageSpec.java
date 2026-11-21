package pl.formbridge.core.model;

import java.util.ArrayList;
import java.util.List;

public final class PageSpec {

    public final String title;
    public final boolean finish;
    public final List<SectionSpec> sections = new ArrayList<>();

    public PageSpec(String title, boolean finish) {
        this.title = title;
        this.finish = finish;
    }

    public static PageSpec main(String title) {
        return new PageSpec(title, false);
    }

    public static PageSpec finish(String title) {
        return new PageSpec(title, true);
    }

    public PageSpec add(SectionSpec s) {
        sections.add(s);
        return this;
    }

    public int fieldCount() {
        return sections.stream().mapToInt(s -> s.fields.size()).sum();
    }
}
