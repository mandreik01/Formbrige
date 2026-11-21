package pl.formbridge.core;

import pl.formbridge.core.model.Condition;
import pl.formbridge.core.model.FieldSpec;
import pl.formbridge.core.model.FieldSpec.Type;
import pl.formbridge.core.model.PageSpec;
import pl.formbridge.core.model.SectionSpec;

import java.util.ArrayList;
import java.util.List;

import static pl.formbridge.core.model.Condition.and;
import static pl.formbridge.core.model.Condition.answerIs;
import static pl.formbridge.core.model.Condition.not;
import static pl.formbridge.core.model.Condition.or;

public final class StandardSections {

    public static final String DATA_PAGE = "Dane";
    public static final String CONTACT_PAGE = "Kontakt i zgody";
    public static final String FINISH_PAGE = "Strona końcowa";

    public static final String SELF = "Swoim";
    public static final String OTHER_PERSON = "Innej osoby";
    public static final String ORGANISATION = "Osoby prawnej lub jednostki organizacyjnej";
    public static final String PROXY = "Pełnomocnik";
    public static final String REPRESENTATIVE = "Reprezentant";

    private static final String SUBJECT = "podmiot_wniosku";
    private static final String ON_BEHALF = "wybierz_w_czyim_imieniu_wnioskujesz";
    private static final String AS_WHO = "wybierz_jako_kto_wnioskujesz";

    public enum ApplicantMode {
        PERSON,
        ALL
    }

    private StandardSections() {
    }

    public static PageSpec dataPage(String office, boolean organ, ApplicantMode mode, String formTitle) {
        PageSpec page = PageSpec.main(DATA_PAGE);
        boolean orgs = mode == ApplicantMode.ALL;
        Condition other = answerIs(SUBJECT, ON_BEHALF, OTHER_PERSON);
        Condition org = answerIs(SUBJECT, ON_BEHALF, ORGANISATION);

        String officeTitle = organ ? "Organ, do którego jest składany wniosek" : "Urząd, do którego jest składany wniosek";
        page.add(SectionSpec.of(officeTitle, TechNames.slug(officeTitle))
                .add(FieldSpec.of(Type.TEXT_FIELD, organ ? "Nazwa organu" : "Nazwa urzędu",
                        organ ? "nazwa_organu" : "nazwa_urzedu").readOnlyValue(office)));

        List<String> subjectOptions = new ArrayList<>(List.of(SELF, OTHER_PERSON));
        if (orgs) {
            subjectOptions.add(ORGANISATION);
        }
        SectionSpec subject = SectionSpec.of("Podmiot wniosku", SUBJECT)
                .add(FieldSpec.of(Type.RADIO, "Wybierz, w czyim imieniu wnioskujesz", ON_BEHALF)
                        .options(subjectOptions).required());
        if (orgs) {
            subject.add(FieldSpec.of(Type.RADIO, "Wybierz, jako kto wnioskujesz", AS_WHO)
                    .options(List.of(PROXY, REPRESENTATIVE)).required().when(org));
        }
        page.add(subject);

        page.add(SectionSpec.of("Twoje dane", "twoje_dane")
                .add(FieldSpec.of(Type.TEXT_FIELD, "Imię", "imie").required().external("MLPZ.name"))
                .add(FieldSpec.of(Type.TEXT_FIELD, "Nazwisko", "nazwisko").required().external("MLPZ.surname"))
                .add(FieldSpec.of(Type.TEXT_FIELD, "Numer PESEL", "numer_pesel").required().validate("PESEL")
                        .external("MLPZ.pesel")));

        page.add(address("Adres zamieszkania", "adres_zamieszkania", "", true));

        if (orgs) {
            page.add(SectionSpec.of("Dane podmiotu", "dane_podmiotu").when(org)
                    .add(FieldSpec.of(Type.TEXT_FIELD, "Nazwa", "nazwa").required())
                    .add(FieldSpec.of(Type.TEXT_FIELD, "NIP", "nip").required().validate("NIP"))
                    .add(FieldSpec.of(Type.TEXT_FIELD, "REGON", "regon").required().validate("REGON"))
                    .add(FieldSpec.of(Type.TEXT_FIELD, "Numer KRS", "numer_krs")
                            .description("Wpisz 10 cyfr, jeśli podmiot jest wpisany do KRS.")));
            page.add(address("Adres siedziby", "adres_siedziby", "_siedziba", false).when(org));
            page.add(SectionSpec.of("Potwierdzenie, że jesteś reprezentantem", "potwierdzenie_ze_jestes_reprezentantem")
                    .description(StandardTexts.get("desc.reprezentant"))
                    .when(and(org, answerIs(SUBJECT, AS_WHO, REPRESENTATIVE)))
                    .add(FieldSpec.of(Type.FILE_UPLOADER, "Plik z odpisem danych", "plik_z_odpisem_danych").required()));
        }

        page.add(SectionSpec.of("Dane innej osoby", "dane_innej_osoby").when(other)
                .add(FieldSpec.of(Type.TEXT_FIELD, "Imię", "imie_inna_osoba").required())
                .add(FieldSpec.of(Type.TEXT_FIELD, "Nazwisko", "nazwisko_inna_osoba").required())
                .add(FieldSpec.of(Type.TEXT_FIELD, "Numer PESEL", "numer_pesel_inna_osoba").required().validate("PESEL")));
        page.add(address("Adres zamieszkania innej osoby", "adres_zamieszkania_osoby_ktorej_jestes_pelnomocnikiem",
                "_inna_osoba", false).when(other));

        Condition proxy = orgs ? or(other, and(org, answerIs(SUBJECT, AS_WHO, PROXY))) : other;
        page.add(SectionSpec.of("Pełnomocnictwo", "pelnomocnictwo").when(proxy)
                .description(StandardTexts.get("desc.pelnomocnictwo"))
                .add(FieldSpec.of(Type.FILE_UPLOADER, "Plik dokumentu z pełnomocnictwem",
                        "plik_dokumentu_z_pelnomocnictwem").required()
                        .description("Dopuszczalne formaty: jpg, jpeg, png, pdf, xml, PAdES, CAdES, XAdES. Maksymalnie 1 plik, do 10 MB.")));

        String feeQuestion = "czy_zlozenie_pelnomocnictwa_wymaga_oplaty_skarbowej";
        String fee1 = "oplata_skarbowa_pelnomocnictwo_1";
        Condition feeYes = answerIs(fee1, feeQuestion, "Tak");
        String feeText = StandardTexts.get("text.oplata1").replace("{{TITLE}}", formTitle);
        page.add(SectionSpec.of("Opłata skarbowa", fee1).when(other)
                .description(StandardTexts.get("desc.oplata1"))
                .add(FieldSpec.of(Type.RADIO, "Czy złożenie pełnomocnictwa wymaga opłaty skarbowej?", feeQuestion)
                        .options(List.of("Tak", "Nie")).required())
                .add(FieldSpec.of(Type.TEXT_BLOCK, "", "oplata_info_1").description(feeText).when(feeYes))
                .add(FieldSpec.of(Type.FILE_UPLOADER, "Plik z potwierdzeniem przelewu", "plik_z_potwierdzeniem_przelewu")
                        .required().when(feeYes).description("Dopuszczalny format: PDF. Maksymalnie 1 plik, do 10 MB.")));

        if (orgs) {
            page.add(SectionSpec.of("Opłata skarbowa za pełnomocnictwo", "oplata_skarbowa_pelnomocnictwo_2")
                    .when(and(org, answerIs(SUBJECT, AS_WHO, PROXY)))
                    .add(FieldSpec.of(Type.TEXT_BLOCK, "", "oplata_info")
                            .description(StandardTexts.get("text.oplata2").replace("{{TITLE}}", formTitle)))
                    .add(FieldSpec.of(Type.FILE_UPLOADER, "Plik z potwierdzeniem przelewu", "plik_z_potwierdzeniem_przelewu_2")
                            .required().description("Dopuszczalny format: PDF. Maksymalnie 1 plik, do 10 MB.")));
        }
        return page;
    }

    public static SectionSpec address(String title, String sectionTech, String suffix, boolean prefill) {
        Condition poland = answerIs(sectionTech, "kraj" + suffix, "Polska");
        Condition abroad = not(poland);
        FieldSpec country = FieldSpec.of(Type.SELECT, "Kraj", "kraj" + suffix).required();
        country.dictionaryName = "Kraje";
        country.multiLevelTree = false;
        country.searchEnabled = true;
        country.defaultValue = List.of("Polska");

        FieldSpec teryt = FieldSpec.of(Type.SELECT, "TERYT_4G_DOWODOWY", "wojewodztwo" + suffix)
                .dictionary("TERYT_4G_DOWODOWY", true).required().when(poland);
        teryt.defaultValue = List.of();
        teryt.tooltipMessage = "Wybierz kolejno województwo, powiat, gminę i miejscowość.";

        FieldSpec zip = FieldSpec.of(Type.TEXT_FIELD, "Kod pocztowy", "kod_pocztowy" + suffix).required()
                .validate("ZIP_CODE").when(poland);
        FieldSpec street = FieldSpec.of(Type.TEXT_FIELD, "Ulica", "ulica" + suffix).required().when(poland)
                .description("Np. Polna, aleja Leśna, plac Lipowy, skwer Słoneczny");
        FieldSpec building = FieldSpec.of(Type.TEXT_FIELD, "Numer budynku", "numer_budynku" + suffix).required();
        FieldSpec flat = FieldSpec.of(Type.TEXT_FIELD, "Numer lokalu", "numer_lokalu" + suffix);
        if (prefill) {
            zip.prefilled("MLPZ.postCode");
            street.prefilled("MLPZ.street");
            building.prefilled("MLPZ.building");
            flat.prefilled("MLPZ.apartment");
        }
        return SectionSpec.of(title, sectionTech)
                .add(country)
                .add(FieldSpec.of(Type.TEXT_FIELD, "Miejscowość", "miejscowosc" + suffix + "_zagranica").required().when(abroad))
                .add(FieldSpec.of(Type.TEXT_FIELD, "Kod pocztowy", "kod_pocztowy" + suffix + "_zagranica").required().when(abroad))
                .add(FieldSpec.of(Type.TEXT_FIELD, "Ulica", "ulica" + suffix + "_zagranica").required().when(abroad))
                .add(teryt)
                .add(zip)
                .add(street)
                .add(building)
                .add(flat);
    }

    public static PageSpec contactPage(List<FieldSpec.Consent> statements) {
        PageSpec page = PageSpec.main(CONTACT_PAGE);
        page.add(SectionSpec.of("Dane do kontaktu", "dane_do_kontaktu")
                .description(StandardTexts.get("desc.kontakt"))
                .add(FieldSpec.of(Type.TEXT_FIELD, "Numer telefonu", "numer_telefonu").prefilled("MLPZ.phoneNumber"))
                .add(FieldSpec.of(Type.TEXT_FIELD, "Adres e-mail", "adres_e_mail").validate("EMAIL")
                        .prefilled("MLPZ.email")));
        FieldSpec consent = FieldSpec.of(Type.CONSENT_SECTION, "", "zgoda");
        consent.consents.addAll(statements);
        consent.consents.add(new FieldSpec.Consent(StandardTexts.get("consent.rodo"), true));
        String title = statements.isEmpty() ? "Zgoda" : "Zgody i oświadczenia";
        page.add(SectionSpec.of(title, "zgoda_wyrazenie").add(consent));
        return page;
    }

    public static PageSpec finishPage() {
        return PageSpec.finish(FINISH_PAGE)
                .add(SectionSpec.of("Podgląd uzupełnionego wniosku", "podglad_uzupelnionego_wniosku")
                        .add(FieldSpec.of(Type.TEXT_BLOCK, "", "podglad_1").description(StandardTexts.get("text.podglad"))));
    }
}
