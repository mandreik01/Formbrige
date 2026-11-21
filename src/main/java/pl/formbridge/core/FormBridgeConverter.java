package pl.formbridge.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.formbridge.core.ai.FormRefiner;
import pl.formbridge.core.ai.LlamaCppClient;
import pl.formbridge.core.analysis.FormAnalyzer;
import pl.formbridge.core.model.FormSpec;
import pl.formbridge.core.model.PageSpec;
import pl.formbridge.core.model.SectionSpec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class FormBridgeConverter implements FormConverter {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FormRefiner refiner;

    public record Result(String json, FormRefiner.Report ai, List<String> proposedDictionaries) {
    }

    public FormBridgeConverter() {
        this(null);
    }

    public FormBridgeConverter(FormRefiner refiner) {
        this.refiner = refiner;
    }

    public static FormBridgeConverter withLocalLlm(List<LlamaCppClient.Endpoint> endpoints, Duration budget) {
        return new FormBridgeConverter(new FormRefiner(new LlamaCppClient(endpoints), budget, 10, 10));
    }

    @Override
    public String convert(ExtractedDocument document) throws FormConversionException {
        return convertDetailed(document).json();
    }

    public Result convertDetailed(ExtractedDocument document) throws FormConversionException {
        FormRefiner.Report[] report = {FormRefiner.Report.skipped()};
        List<String> dictionaries = new ArrayList<>();
        ObjectNode json = build(document, report, dictionaries);
        return new Result(toJson(json), report[0], List.copyOf(dictionaries));
    }

    public ObjectNode convertToNode(ExtractedDocument document) throws FormConversionException {
        return build(document, new FormRefiner.Report[]{FormRefiner.Report.skipped()}, new ArrayList<>());
    }

    private ObjectNode build(ExtractedDocument document, FormRefiner.Report[] report, List<String> dictionaries)
            throws FormConversionException {
        String text = document.text();
        if (text == null || text.strip().length() < 20) {
            throw new FormConversionException("Nie udało się odczytać treści formularza z pliku " + document.fileName());
        }
        FormSpec form = buildSpec(document, report);
        dictionaries.addAll(form.proposedDictionaries);
        ObjectNode json = new FormRenderer(mapper).render(form);
        List<String> problems = FormRules.check(json);
        if (!problems.isEmpty()) {
            throw new FormConversionException("Wygenerowany formularz narusza Reguły techniczne: " + String.join("; ", problems));
        }
        return json;
    }

    public FormSpec buildSpec(ExtractedDocument document) {
        return buildSpec(document, new FormRefiner.Report[1]);
    }

    private FormSpec buildSpec(ExtractedDocument document, FormRefiner.Report[] report) {
        TechNames.Registry names = new TechNames.Registry();
        reserveStandardNames(names);
        FormAnalyzer.Analysis a = new FormAnalyzer(names).analyze(document.fileName(), document.text());
        List<PageSpec> body = new ArrayList<>(a.pages());
        if (refiner != null) {
            report[0] = refiner.refine(a.title(), body, names::unique);
        }
        FormSpec form = new FormSpec(a.title());
        form.proposedDictionaries.addAll(a.proposedDictionaries());
        form.add(StandardSections.dataPage(a.office(), a.organ(), a.mode(), a.title()));
        body.forEach(form::add);
        form.add(StandardSections.contactPage(a.statements()));
        form.add(StandardSections.finishPage());
        return form;
    }

    private static void reserveStandardNames(TechNames.Registry names) {
        for (StandardSections.ApplicantMode mode : StandardSections.ApplicantMode.values()) {
            for (PageSpec p : List.of(StandardSections.dataPage("x", false, mode, "x"), StandardSections.dataPage("x", true, mode, "x"),
                    StandardSections.contactPage(List.of()), StandardSections.finishPage())) {
                for (SectionSpec s : p.sections) {
                    names.reserve(s.techName);
                    s.fields.forEach(f -> names.reserve(f.techName));
                }
            }
        }
    }

    private String toJson(ObjectNode node) throws FormConversionException {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new FormConversionException("Błąd serializacji JSON", e);
        }
    }
}
