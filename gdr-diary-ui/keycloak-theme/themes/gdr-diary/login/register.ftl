<#import "template.ftl" as layout>
<#import "user-profile-commons.ftl" as userProfileCommons>
<@layout.registrationLayout displayMessage=messagesPerField.exists('global') displayRequiredFields=true displayInfo=true wide=true; section>
    <#if section = "header">
        ${msg("gdrRegisterTitle")}
    <#elseif section = "form">
        <form id="kc-register-form" action="${url.registrationAction}" method="post" data-gdr-single-submit class="grid grid-cols-1 gap-x-5 gap-y-5 min-[640px]:grid-cols-2">
            <@userProfileCommons.userProfileFormFields; callback, attribute>
            </@userProfileCommons.userProfileFormFields>

            <#if passwordRequired??>
                <div class="${properties.kcFormGroupClass!}">
                    <label for="password" class="${properties.kcLabelClass!}">${msg("password")} <span class="${properties.kcInputRequiredClass!}">*</span></label>
                    <div class="${properties.kcInputGroup!}" dir="ltr">
                        <input type="password" id="password" name="password" autocomplete="new-password" class="${properties.kcInputClass!} pe-[52px]" aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true<#else>false</#if>">
                        <@layout.passwordToggle target="password"/>
                    </div>
                    <#if messagesPerField.existsError('password')>
                        <span id="input-error-password" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
                    </#if>
                </div>
                <div class="${properties.kcFormGroupClass!}">
                    <label for="password-confirm" class="${properties.kcLabelClass!}">${msg("passwordConfirm")} <span class="${properties.kcInputRequiredClass!}">*</span></label>
                    <div class="${properties.kcInputGroup!}" dir="ltr">
                        <input type="password" id="password-confirm" name="password-confirm" autocomplete="new-password" class="${properties.kcInputClass!} pe-[52px]" aria-invalid="<#if messagesPerField.existsError('password-confirm')>true<#else>false</#if>">
                        <@layout.passwordToggle target="password-confirm"/>
                    </div>
                    <#if messagesPerField.existsError('password-confirm')>
                        <span id="input-error-password-confirm" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <#if termsAcceptanceRequired??>
                <div class="${properties.kcFormGroupClass!} col-span-full">
                    <p class="m-0 text-base text-soft">${kcSanitize(msg("termsText"))?no_esc}</p>
                    <label class="inline-flex min-h-[44px] items-center gap-2.5 text-base text-soft">
                        <input type="checkbox" id="termsAccepted" name="termsAccepted" class="${properties.kcCheckboxInputClass!}" aria-invalid="<#if messagesPerField.existsError('termsAccepted')>true<#else>false</#if>">
                        ${msg("acceptTerms")}
                    </label>
                    <#if messagesPerField.existsError('termsAccepted')>
                        <span id="input-error-terms-accepted" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.get('termsAccepted'))?no_esc}</span>
                    </#if>
                </div>
            </#if>

            <div class="${properties.kcFormButtonsClass!} col-span-full">
                <button type="submit" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}">${msg("doRegister")}</button>
            </div>
        </form>
    <#elseif section = "info">
        <div id="kc-registration" class="flex flex-wrap items-center justify-center gap-x-2">
            ${msg("gdrHaveAccount")}
            <a href="${url.loginUrl}" class="${properties.kcLinkClass!}">${msg("gdrLogin")}</a>
        </div>
    </#if>
</@layout.registrationLayout>
