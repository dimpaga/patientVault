package com.example.patientvault.search;

/**
 * The patient values that are searchable. They are only used transiently to compute blind-index tokens;
 * nothing readable is stored by the search module.
 *
 * @param mobileE164 mobile phone in E.164 form
 * @param amkaDigits the 11 AMKA digits without separators
 */
public record IndexedFields(String firstName, String lastName, String email, String mobileE164, String amkaDigits) {

    @Override
    public String toString() {
        return "IndexedFields[...]";
    }
}
