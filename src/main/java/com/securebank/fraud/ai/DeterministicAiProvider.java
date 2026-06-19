package com.securebank.fraud.ai;

import com.securebank.fraud.support.SupportedLocale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * ADAPTER #2 — the fully offline, deterministic fallback provider.
 *
 * <p>It needs no network, no API key, and never fails. It produces helpful, localized
 * (en/hi/mr) answers using simple keyword routing and templates. This is what the service
 * runs on by default (blank API key) and what it falls back to whenever the LLM is
 * disabled, unconfigured, or the circuit breaker is open.</p>
 *
 * <p>Marked {@code isLlm() == false} so callers can correctly report {@code from_llm=false}
 * on the gRPC reply.</p>
 */
@Slf4j
@Component
public class DeterministicAiProvider implements AiProvider {

    @Override
    public String answer(String question, SupportedLocale locale) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        // Very small intent router. Real product would be richer, but this guarantees a
        // sensible, localized response with zero dependencies.
        Intent intent = classify(q);
        String text = switch (locale) {
            case HI -> hindi(intent);
            case MR -> marathi(intent);
            case EN -> english(intent);
        };
        log.debug("DeterministicAiProvider answered intent={} locale={}", intent, locale);
        return text;
    }

    @Override
    public String summarize(String breakdownDescription, SupportedLocale locale) {
        // Templated summary that simply frames the precomputed facts in the right language.
        return switch (locale) {
            case HI -> "आपके हाल के खर्च का सारांश: " + breakdownDescription
                    + " बड़ी श्रेणियों पर नज़र रखें और बजट निर्धारित करें.";
            case MR -> "तुमच्या अलीकडील खर्चाचा सारांश: " + breakdownDescription
                    + " मोठ्या श्रेणींवर लक्ष ठेवा आणि बजेट ठरवा.";
            case EN -> "Summary of your recent spending: " + breakdownDescription
                    + " Keep an eye on the largest categories and consider a budget.";
        };
    }

    @Override
    public boolean isLlm() {
        return false;
    }

    // ------------------------------------------------------------------
    // Tiny keyword-based intent classifier + localized templates.
    // ------------------------------------------------------------------

    private enum Intent { BALANCE, TRANSFER, FRAUD, SPENDING, GREETING, UNKNOWN }

    private Intent classify(String q) {
        if (q.isBlank()) {
            return Intent.GREETING;
        }
        if (containsAny(q, "balance", "बैलेंस", "शिल्लक")) {
            return Intent.BALANCE;
        }
        if (containsAny(q, "transfer", "send money", "pay", "ट्रांसफर", "पैसे", "पैसा")) {
            return Intent.TRANSFER;
        }
        if (containsAny(q, "fraud", "scam", "suspicious", "धोखा", "फसवणूक", "संदिग्ध")) {
            return Intent.FRAUD;
        }
        if (containsAny(q, "spend", "spending", "expense", "खर्च", "खर्च")) {
            return Intent.SPENDING;
        }
        if (containsAny(q, "hi", "hello", "hey", "नमस्ते", "नमस्कार")) {
            return Intent.GREETING;
        }
        return Intent.UNKNOWN;
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private String english(Intent intent) {
        return switch (intent) {
            case BALANCE -> "You can view your current balance on the Accounts dashboard. "
                    + "Each account card shows its available and ledger balance.";
            case TRANSFER -> "To transfer money, open Payments, choose a beneficiary, enter the amount, "
                    + "and confirm. Large or first-time transfers may be reviewed for your safety.";
            case FRAUD -> "If you suspect fraud, do not approve the transaction. SecureBank automatically "
                    + "scores every transfer and may block or flag suspicious activity.";
            case SPENDING -> "Your spending insights group transactions by category. "
                    + "Open Insights to see where your money goes this month.";
            case GREETING -> "Hello! I'm the SecureBank assistant. Ask me about balances, transfers, "
                    + "spending insights, or account safety.";
            case UNKNOWN -> "I'm the SecureBank assistant. I can help with balances, transfers, "
                    + "spending insights, and account safety. Could you rephrase your question?";
        };
    }

    private String hindi(Intent intent) {
        return switch (intent) {
            case BALANCE -> "आप अपना मौजूदा बैलेंस Accounts डैशबोर्ड पर देख सकते हैं। "
                    + "हर अकाउंट कार्ड पर उपलब्ध और लेज़र बैलेंस दिखता है।";
            case TRANSFER -> "पैसे ट्रांसफर करने के लिए Payments खोलें, लाभार्थी चुनें, राशि दर्ज करें "
                    + "और पुष्टि करें। बड़ी या पहली बार की ट्रांसफर आपकी सुरक्षा के लिए जाँची जा सकती है।";
            case FRAUD -> "अगर आपको धोखाधड़ी का संदेह है तो लेन-देन स्वीकृत न करें। SecureBank हर ट्रांसफर को "
                    + "स्वतः स्कोर करता है और संदिग्ध गतिविधि को रोक या चिह्नित कर सकता है।";
            case SPENDING -> "आपके खर्च की जानकारी लेन-देन को श्रेणी के अनुसार समूहित करती है। "
                    + "इस महीने आपका पैसा कहाँ जा रहा है, यह देखने के लिए Insights खोलें।";
            case GREETING -> "नमस्ते! मैं SecureBank सहायक हूँ। मुझसे बैलेंस, ट्रांसफर, खर्च की जानकारी "
                    + "या खाते की सुरक्षा के बारे में पूछें।";
            case UNKNOWN -> "मैं SecureBank सहायक हूँ। मैं बैलेंस, ट्रांसफर, खर्च की जानकारी और खाते की "
                    + "सुरक्षा में मदद कर सकता हूँ। कृपया अपना प्रश्न दोबारा पूछें।";
        };
    }

    private String marathi(Intent intent) {
        return switch (intent) {
            case BALANCE -> "तुम्ही तुमची सध्याची शिल्लक Accounts डॅशबोर्डवर पाहू शकता. "
                    + "प्रत्येक अकाउंट कार्डवर उपलब्ध आणि लेजर शिल्लक दिसते.";
            case TRANSFER -> "पैसे ट्रान्सफर करण्यासाठी Payments उघडा, लाभार्थी निवडा, रक्कम भरा "
                    + "आणि पुष्टी करा. मोठ्या किंवा पहिल्यांदाच्या ट्रान्सफर तुमच्या सुरक्षेसाठी तपासल्या जाऊ शकतात.";
            case FRAUD -> "तुम्हाला फसवणुकीचा संशय असल्यास व्यवहार मंजूर करू नका. SecureBank प्रत्येक ट्रान्सफरला "
                    + "आपोआप स्कोअर करते आणि संशयास्पद हालचाल रोखू किंवा चिन्हांकित करू शकते.";
            case SPENDING -> "तुमच्या खर्चाची माहिती व्यवहार श्रेणीनुसार गटात विभागते. "
                    + "या महिन्यात तुमचे पैसे कुठे जातात हे पाहण्यासाठी Insights उघडा.";
            case GREETING -> "नमस्कार! मी SecureBank सहाय्यक आहे. मला शिल्लक, ट्रान्सफर, खर्चाची माहिती "
                    + "किंवा खात्याच्या सुरक्षेबद्दल विचारा.";
            case UNKNOWN -> "मी SecureBank सहाय्यक आहे. मी शिल्लक, ट्रान्सफर, खर्चाची माहिती आणि खात्याची "
                    + "सुरक्षा यामध्ये मदत करू शकतो. कृपया तुमचा प्रश्न पुन्हा विचारा.";
        };
    }
}
