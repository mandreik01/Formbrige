package pl.formbridge.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.formbridge.core.model.FieldSpec;
import pl.formbridge.core.model.FormSpec;
import pl.formbridge.core.model.PageSpec;
import pl.formbridge.core.model.SectionSpec;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FormRenderer {

    static final int COLS = 3;

    private final ObjectMapper mapper;

    public FormRenderer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode render(FormSpec form) {
        Map<String, String> tabOfSection = new HashMap<>();
        for (PageSpec p : form.pages) {
            for (SectionSpec s : p.sections) {
                tabOfSection.putIfAbsent(s.techName, p.title);
            }
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("title", form.title);
        ArrayNode tabs = root.putArray("tabs");
        String[] tabIds = new String[form.pages.size()];
        for (int i = 0; i < form.pages.size(); i++) {
            PageSpec page = form.pages.get(i);
            tabIds[i] = id(form, "tab/" + i + "/" + page.title);
            ObjectNode tab = tabs.addObject();
            tab.put("id", tabIds[i]);
            tab.put("title", page.title);
            tab.put("type", page.finish ? "FINISH" : "MAIN");
            ArrayNode questions = tab.putArray("questions");
            int y = 0;
            for (SectionSpec section : page.sections) {
                int rows = section.fields.size() + 1;
                questions.add(sectionNode(form, page, section, y, rows, tabOfSection));
                y += rows;
            }
        }
        ArrayNode links = root.putObject("flow").putArray("links");
        for (int i = 0; i + 1 < tabIds.length; i++) {
            links.addObject()
                    .put("id", id(form, "link/" + i))
                    .put("source", tabIds[i])
                    .put("target", tabIds[i + 1]);
        }
        return root;
    }

    private ObjectNode sectionNode(FormSpec form, PageSpec page, SectionSpec section, int y, int rows,
                                   Map<String, String> tabOfSection) {
        ObjectNode q = mapper.createObjectNode();
        q.put("x", 0).put("y", y).put("rows", rows).put("cols", COLS);
        if (section.minItemRows != null) {
            q.put("minItemRows", section.minItemRows);
        }
        ObjectNode f = q.putObject("field");
        f.put("id", id(form, "section/" + page.title + "/" + section.techName));
        f.put("type", section.repeating ? "REPEATING_SECTION" : "SECTION");
        f.put("techName", section.techName);
        f.put("title", section.title);
        f.put("description", htmlOrEmpty(section.description, true));
        f.putArray("options");
        f.putArray("validation");
        f.put("readOnly", false);
        ArrayNode grid = f.putArray("repeatingSectionGrid");
        for (int i = 0; i < section.fields.size(); i++) {
            grid.add(fieldNode(form, page, section, section.fields.get(i), i, tabOfSection));
        }
        states(f, section.showWhen == null ? null : section.showWhen.toJsonata(tabOfSection::get));
        f.put("questionOrder", y);
        f.put("tooltipMessage", "");
        return q;
    }

    private ObjectNode fieldNode(FormSpec form, PageSpec page, SectionSpec section, FieldSpec spec, int y,
                                 Map<String, String> tabOfSection) {
        ObjectNode q = mapper.createObjectNode();
        q.put("x", 0).put("y", y).put("rows", 1).put("cols", COLS);
        ObjectNode f = q.putObject("field");
        f.put("id", id(form, "field/" + page.title + "/" + section.techName + "/" + spec.techName));
        f.put("type", spec.type.name());
        f.put("techName", spec.techName);
        f.put("title", spec.title);
        f.put("description", htmlOrEmpty(spec.description, spec.type == FieldSpec.Type.TEXT_BLOCK
                || spec.type == FieldSpec.Type.INFO_SECTION));
        if (spec.dictionaryName != null) {
            f.put("dictionaryName", spec.dictionaryName);
        }
        ArrayNode options = f.putArray("options");
        for (String o : spec.options) {
            options.addObject().put("id", o).put("name", o).put("techName", TechNames.slug(o));
        }
        if (spec.multiLevelTree != null) {
            f.put("multiLevelTree", spec.multiLevelTree);
        }
        ArrayNode validation = f.putArray("validation");
        spec.validation.forEach(validation::add);
        if (spec.searchEnabled != null) {
            f.put("searchEnabled", spec.searchEnabled);
        }
        f.put("readOnly", spec.readOnly);
        f.putArray("repeatingSectionGrid");
        if (spec.type == FieldSpec.Type.CONSENT_SECTION) {
            ObjectNode cfg = f.putObject("consentSectionConfig");
            ArrayNode consents = cfg.putArray("consents");
            for (FieldSpec.Consent c : spec.consents) {
                consents.addObject().put("description", c.descriptionHtml()).put("required", c.required());
            }
            cfg.putArray("files");
        }
        if (spec.defaultValue instanceof List<?> list) {
            ArrayNode dv = f.putArray("defaultValue");
            list.forEach(v -> dv.add(String.valueOf(v)));
        } else if (spec.defaultValue != null) {
            f.put("defaultValue", spec.defaultValue.toString());
        }
        if (spec.defaultValueType != null) {
            f.put("defaultValueType", spec.defaultValueType);
        }
        states(f, spec.showWhen == null ? null : spec.showWhen.toJsonata(tabOfSection::get));
        f.put("questionOrder", y);
        f.put("tooltipMessage", spec.tooltipMessage == null ? "" : spec.tooltipMessage);
        return q;
    }

    private static void states(ObjectNode f, String query) {
        ArrayNode states = f.putArray("states");
        if (query != null) {
            states.addObject().put("type", "SHOW").put("query", query);
        }
    }

    private static String htmlOrEmpty(String text, boolean alwaysHtml) {
        if (text == null || text.isBlank()) {
            return alwaysHtml ? "<html><head></head><body></body></html>" : "";
        }
        if (text.startsWith("<html")) {
            return text;
        }
        return StandardTexts.html(text);
    }

    private static String id(FormSpec form, String path) {
        return UUID.nameUUIDFromBytes((form.title + "|" + path).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
