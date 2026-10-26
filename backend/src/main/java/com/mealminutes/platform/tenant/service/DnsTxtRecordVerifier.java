package com.mealminutes.platform.tenant.service;

import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

@Component
public class DnsTxtRecordVerifier implements DomainOwnershipVerifier {
    @Override
    public boolean hasTxtRecord(String recordName, String expectedValue) {
        if (recordName == null || recordName.isBlank() || expectedValue == null || expectedValue.isBlank()) return false;
        Hashtable<String, String> environment = new Hashtable<>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "2000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        DirContext context = null;
        try {
            context = new InitialDirContext(environment);
            Attributes attributes = context.getAttributes(recordName, new String[]{"TXT"});
            Attribute txt = attributes.get("TXT");
            if (txt == null) return false;
            for (int i = 0; i < txt.size(); i++) {
                String value = String.valueOf(txt.get(i)).replace("\"", "").trim();
                if (expectedValue.equals(value)) return true;
            }
            return false;
        } catch (NamingException exception) {
            return false;
        } finally {
            if (context != null) {
                try { context.close(); } catch (NamingException ignored) { }
            }
        }
    }
}