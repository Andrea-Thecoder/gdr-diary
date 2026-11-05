<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
    <#if section = "header">
        ${msg("updatePasswordTitle")}
    <#elseif section = "form">
        <form id="kc-passwd-update-form" action="${url.loginAction}" method="post" data-gdr-single-submit class="${properties.kcFormClass!}">
            <#-- Campi nascosti per i gestori di password: sanno a quale utente si cambia la password. -->
            <input type="text" id="username" name="username" value="${(username!'')}" autocomplete="username" readonly tabindex="-1" aria-hidden="true" class="hidden">
            <input type="password" id="password" name="password" autocomplete="current-password" tabindex="-1" aria-hidden="true" class="hidden">

            <div class="${properties.kcFormGroupClass!}">
                <label for="password-new" class="${properties.kcLabelClass!}">${msg("passwordNew")}</label>
                <div class="${properties.kcInputGroup!}" dir="ltr">
                    <input type="password" id="password-new" name="password-new" autofocus autocomplete="new-password" class="${properties.kcInputClass!} pe-[52px]" aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true<#else>false</#if>"<#if messagesPerField.existsError('password')> aria-describedby="input-error-password"</#if>>
                    <@layout.passwordToggle target="password-new"/>
                </div>
                <#if messagesPerField.existsError('password')>
                    <span id="input-error-password" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
                </#if>
            </div>

            <div class="${properties.kcFormGroupClass!}">
                <label for="password-confirm" class="${properties.kcLabelClass!}">${msg("passwordConfirm")}</label>
                <div class="${properties.kcInputGroup!}" dir="ltr">
                    <input type="password" id="password-confirm" name="password-confirm" autocomplete="new-password" class="${properties.kcInputClass!} pe-[52px]" aria-invalid="<#if messagesPerField.existsError('password-confirm')>true<#else>false</#if>"<#if messagesPerField.existsError('password-confirm')> aria-describedby="input-error-password-confirm"</#if>>
                    <@layout.passwordToggle target="password-confirm"/>
                </div>
                <#if messagesPerField.existsError('password-confirm')>
                    <span id="input-error-password-confirm" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
                </#if>
            </div>

            <#if isAppInitiatedAction??>
                <label class="inline-flex min-h-[44px] items-center gap-2.5 text-base text-soft">
                    <input type="checkbox" id="logout-sessions" name="logout-sessions" value="on" checked class="${properties.kcCheckboxInputClass!}">
                    ${msg("logoutOtherSessions")}
                </label>
            </#if>

            <div id="kc-form-buttons" class="mt-1 flex flex-col gap-3 min-[480px]:flex-row">
                <button type="submit" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} min-[480px]:order-2 min-[480px]:flex-1">${msg("gdrContinue")}</button>
                <#if isAppInitiatedAction??>
                    <button type="submit" name="cancel-aia" value="true" formnovalidate class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} min-h-[54px]! text-[15px]! min-[480px]:order-1 min-[480px]:flex-1">${msg("doCancel")}</button>
                </#if>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
