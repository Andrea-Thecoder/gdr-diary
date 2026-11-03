<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("gdrLoginTitle")}
    <#elseif section = "form">
        <#if realm.password>
            <form id="kc-form-login" action="${url.loginAction}" method="post" data-gdr-single-submit class="${properties.kcFormClass!}">
                <#if !usernameHidden??>
                    <div class="${properties.kcFormGroupClass!}">
                        <label for="username" class="${properties.kcLabelClass!}"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>
                        <input id="username" name="username" type="text" value="${(login.username!'')}" autofocus autocomplete="username" class="${properties.kcInputClass!}" aria-invalid="<#if messagesPerField.existsError('username','password')>true<#else>false</#if>"<#if messagesPerField.existsError('username','password')> aria-describedby="input-error"</#if>>
                        <#if messagesPerField.existsError('username','password')>
                            <span id="input-error" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                        </#if>
                    </div>
                </#if>

                <div class="${properties.kcFormGroupClass!}">
                    <label for="password" class="${properties.kcLabelClass!}">${msg("password")}</label>
                    <div class="${properties.kcInputGroup!}" dir="ltr">
                        <input id="password" name="password" type="password" autocomplete="current-password" class="${properties.kcInputClass!} pe-[52px]" aria-invalid="<#if messagesPerField.existsError('username','password')>true<#else>false</#if>">
                        <@layout.passwordToggle target="password"/>
                    </div>
                    <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                        <span id="input-error" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
                    </#if>
                </div>

                <div class="${properties.kcFormOptionsClass!}">
                    <#if realm.rememberMe && !usernameHidden??>
                        <label class="inline-flex min-h-[44px] items-center gap-2.5 text-base text-soft">
                            <input id="rememberMe" name="rememberMe" type="checkbox" class="${properties.kcCheckboxInputClass!}"<#if login.rememberMe??> checked</#if>>
                            ${msg("rememberMe")}
                        </label>
                    </#if>
                    <#if realm.resetPasswordAllowed>
                        <a href="${url.loginResetCredentialsUrl}" class="${properties.kcLinkClass!} text-base">${msg("doForgotPassword")}</a>
                    </#if>
                </div>

                <div class="${properties.kcFormButtonsClass!}">
                    <input type="hidden" id="id-hidden-input" name="credentialId"<#if auth.selectedCredential?has_content> value="${auth.selectedCredential}"</#if>>
                    <button type="submit" id="kc-login" name="login" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}">${msg("doLogIn")}</button>
                </div>
            </form>
        </#if>
    <#elseif section = "info">
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration" class="flex flex-wrap items-center justify-center gap-x-2">
                ${msg("gdrNoAccount")}
                <a href="${url.registrationUrl}" class="${properties.kcLinkClass!}">${msg("gdrRegister")}</a>
            </div>
        </#if>
    <#elseif section = "socialProviders">
        <#if realm.password && social?? && social.providers?? && social.providers?has_content>
            <div id="kc-social-providers" class="mt-6 flex flex-col gap-3">
                <p class="m-0 text-center text-base text-soft">${msg("gdrOr")}</p>
                <#list social.providers as p>
                    <a id="social-${p.alias}" href="${p.loginUrl}" class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonBlockClass!}">${p.displayName!}</a>
                </#list>
            </div>
        </#if>
    </#if>
</@layout.registrationLayout>
