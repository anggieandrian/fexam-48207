package com.hand.demo.infra.util;

import com.hand.demo.domain.entity.InvCountHeader;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;

import java.util.Map;

public class Utils {

//    public static void generateCountNumber(InvCountHeader invCountHeader, String codeRule, Map<String, String> variable, CodeRuleBuilder codeRuleBuilder) {
//        if (codeRule == null || codeRule.isEmpty()) {
//            throw new IllegalArgumentException("Code rule cannot be null or empty.");
//        }
//
//        if (variable == null || variable.isEmpty()) {
//            throw new IllegalArgumentException("Variable map cannot be null or empty.");
//        }
//
//        // Generate the document number
//        String docNumber = codeRuleBuilder.generateCode(codeRule, variable);
//
//        // Set the document number on the header
//        invCountHeader.setCountNumber(docNumber);
//
//        // Set the default status
//        invCountHeader.setCountStatus(InvCountHeader.Status.DRAFT);
//
//        // Log for debugging purposes
//        System.out.println("Generated Count Number: " + docNumber);
//    }

}