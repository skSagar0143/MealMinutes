package com.mealminutes.platform.tenant.service;

import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Component
public class DnsTxtRecordVerifier implements DomainOwnershipVerifier {
    private final TxtLookup lookup;

    public DnsTxtRecordVerifier() { this(DnsTxtRecordVerifier::lookupDns); }

    DnsTxtRecordVerifier(TxtLookup lookup) { this.lookup = lookup; }

    @Override
    public boolean hasTxtRecord(String recordName, String expectedValue) {
        if (recordName == null || recordName.isBlank() || expectedValue == null || expectedValue.isBlank()) return false;
        try {
            return lookup.lookup(recordName).stream()
                    .map(value -> value.replace("\"", "").trim())
                    .anyMatch(expectedValue::equals);
        } catch (NamingException exception) {
            return false;
        }
    }

    private static List<String> lookupDns(String recordName) throws NamingException {
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "2000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = null;
        try {
            context = new InitialDirContext(environment);
            Attributes attributes = context.getAttributes(recordName, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");
            if (txt == null) return List.of();
            List<String> values = new ArrayList<>();
            for (int i = 0; i < txt.size(); i++) values.add(String.valueOf(txt.get(i)));
            return values;
        } finally {
            if (context != null) context.close();
        }
    }

    @FunctionalInterface
    interface TxtLookup {
        List<String> lookup(String recordName) throws NamingException;
    }
}