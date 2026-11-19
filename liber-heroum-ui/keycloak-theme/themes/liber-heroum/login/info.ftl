<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>${msg(messageHeader)}<#else>${kcSanitize(message.summary)?no_esc}</#if>
    <#elseif section = "form">
        <#-- Dopo l’eliminazione dell’account non c’è più un diario a cui tornare: si torna alla home. -->
        <#-- Keycloak passa il titolo già tradotto: si confronta con il testo, non con la chiave. -->
        <#assign accountDeleted = (messageHeader!"") == msg("userDeletedSuccessfully") || (message.summary!"") == msg("userDeletedSuccessfully")>
        <#-- Dopo la conferma del cambio email Keycloak indica la home dell'app: il posto giusto è il profilo.
             Keycloak passa il titolo già tradotto, non la chiave. -->
        <#assign emailChanged = (messageHeader!"") == msg("emailUpdatedTitle")>
        <#assign backLabel = accountDeleted?then("lhBackToHome", "lhBackToApp")>
        <#assign redirectLabel = accountDeleted?then("lhRedirectingHome", "lhRedirecting")>
        <#-- Con un’azione ancora da fare il pulsante non riporta al diario: porta al passo successivo. -->
        <#if requiredActions??>
            <#assign backLabel = "lhContinue">
        <#elseif emailChanged>
            <#assign backLabel = "lhBackToProfile">
            <#assign redirectLabel = "lhRedirectingProfile">
        </#if>
        <#assign backUrl = "">
        <#if !skipLink??>
            <#if accountDeleted && (client.baseUrl)?has_content>
                <#assign backUrl = client.baseUrl>
            <#elseif emailChanged && (client.baseUrl)?has_content>
                <#assign backUrl = client.baseUrl?remove_ending("/") + "/profile">
            <#elseif pageRedirectUri?has_content>
                <#assign backUrl = pageRedirectUri>
            <#elseif actionUri?has_content>
                <#assign backUrl = actionUri>
            <#elseif (client.baseUrl)?has_content>
                <#assign backUrl = client.baseUrl>
            </#if>
        </#if>
        <#assign emailRequested = isAppInitiatedAction?? && (messageHeader!"") == "emailUpdateConfirmationSentTitle">
        <#assign succeeded = ((message.type)!"") == "success">
        <#assign goBackAlone = backUrl?has_content && !requiredActions?? && (emailRequested || succeeded || accountDeleted)>
        <#assign findBackAlone = !backUrl?has_content && emailRequested>
        <div id="kc-info-message" class="flex flex-col gap-5"<#if goBackAlone> data-lh-redirect="${backUrl}" data-lh-redirect-after="4"<#elseif findBackAlone> data-lh-redirect-from-flow data-lh-redirect-after="4"</#if>>
            <p class="m-0 text-center text-[20px] text-ink"><#if requiredActions??>${msg("lhOneMoreStep")}<#else>${kcSanitize(message.summary)?no_esc}</#if><#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#items></b></#list></#if></p>
            <#if goBackAlone>
                <p class="m-0 text-center text-base text-soft" aria-live="polite">${msg(redirectLabel)}</p>
            </#if>
            <#if findBackAlone>
                <div hidden data-lh-flow-redirect class="flex flex-col gap-5">
                    <p class="m-0 text-center text-base text-soft" aria-live="polite">${msg(redirectLabel)}</p>
                    <a href="#" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} no-underline">${msg(backLabel)}</a>
                </div>
            </#if>
            <#if backUrl?has_content>
                <a href="${backUrl}" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} no-underline">${msg(backLabel)}</a>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
