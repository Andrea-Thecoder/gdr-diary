<#import "template.ftl" as layout>
<#import "user-profile-commons.ftl" as userProfileCommons>
<@layout.registrationLayout displayMessage=messagesPerField.exists('global') displayRequiredFields=true; section>
    <#if section = "header">
        ${msg("updateEmailTitle")}
    <#elseif section = "form">
        <form id="kc-update-email-form" action="${url.loginAction}" method="post" data-gdr-single-submit class="${properties.kcFormClass!}">
            <@userProfileCommons.userProfileFormFields; callback, attribute>
            </@userProfileCommons.userProfileFormFields>

            <div id="kc-form-buttons" class="mt-1 flex flex-col gap-3 min-[480px]:flex-row">
                <button type="submit" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} min-[480px]:order-2 min-[480px]:flex-1">${msg("gdrContinue")}</button>
                <#if isAppInitiatedAction??>
                    <button type="submit" name="cancel-aia" value="true" formnovalidate class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} min-h-[54px]! text-[15px]! min-[480px]:order-1 min-[480px]:flex-1">${msg("doCancel")}</button>
                </#if>
            </div>
        </form>
    </#if>
</@layout.registrationLayout>
