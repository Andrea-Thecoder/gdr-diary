<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>${msg(messageHeader)}<#else>${kcSanitize(message.summary)?no_esc}</#if>
    <#elseif section = "form">
        <#assign backUrl = "">
        <#if !skipLink??>
            <#if pageRedirectUri?has_content>
                <#assign backUrl = pageRedirectUri>
            <#elseif actionUri?has_content>
                <#assign backUrl = actionUri>
            <#elseif (client.baseUrl)?has_content>
                <#assign backUrl = client.baseUrl>
            </#if>
        </#if>
        <#assign emailRequested = isAppInitiatedAction?? && (messageHeader!"") == "emailUpdateConfirmationSentTitle">
        <#assign succeeded = ((message.type)!"") == "success">
        <#assign goBackAlone = backUrl?has_content && !requiredActions?? && (emailRequested || succeeded)>
        <#assign findBackAlone = !backUrl?has_content && emailRequested>
        <div id="kc-info-message" class="flex flex-col gap-5"<#if goBackAlone> data-gdr-redirect="${backUrl}" data-gdr-redirect-after="4"<#elseif findBackAlone> data-gdr-redirect-from-flow data-gdr-redirect-after="4"</#if>>
            <p class="m-0 text-center text-[20px] text-ink">${kcSanitize(message.summary)?no_esc}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#items></b></#list></#if></p>
            <#if goBackAlone>
                <p class="m-0 text-center text-base text-soft" aria-live="polite">${msg("gdrRedirecting")}</p>
            </#if>
            <#if findBackAlone>
                <div hidden data-gdr-flow-redirect class="flex flex-col gap-5">
                    <p class="m-0 text-center text-base text-soft" aria-live="polite">${msg("gdrRedirecting")}</p>
                    <a href="#" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} no-underline">${msg("gdrBackToApp")}</a>
                </div>
            </#if>
            <#if backUrl?has_content>
                <a href="${backUrl}" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} no-underline">${msg("gdrBackToApp")}</a>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
