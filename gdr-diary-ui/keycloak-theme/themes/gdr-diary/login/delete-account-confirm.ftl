<#-- Nessuna seconda conferma: l’utente ha già scelto nel dialogo dell’app e i suoi dati sono già stati cancellati.
     Qui Keycloak completa solo l’eliminazione dell’accesso; la pagina invia da sola il modulo. -->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${msg("gdrDeleteTitle")}
    <#elseif section = "form">
        <form id="kc-delete-account-form" action="${url.loginAction}" method="post" data-gdr-auto-submit aria-labelledby="kc-page-title" class="m-0 flex flex-col items-center gap-5">
            <input type="hidden" name="accept" value="true">
            <p class="m-0 text-center text-[20px] text-ink" role="status" aria-live="polite">${msg("gdrDeleteProgress")}</p>
            <p class="m-0 text-center text-base text-soft">${msg("gdrDeleteWait")}</p>
            <noscript>
                <button type="submit" id="kc-accept" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} min-h-[54px] px-8 text-[15px] font-semibold tracking-[.12em]">${msg("gdrDeleteContinue")}</button>
            </noscript>
        </form>
    </#if>
</@layout.registrationLayout>
