<#macro passwordToggle target>
<button type="button" class="${properties.kcFormPasswordVisibilityButtonClass!}" aria-controls="${target}" aria-label="${msg("gdrShowPassword")}" data-gdr-password-toggle="${target}" data-label-show="${msg("gdrShowPassword")}" data-label-hide="${msg("gdrHidePassword")}">
    <svg data-icon-show width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></svg>
    <svg data-icon-hide hidden width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 3l18 18"/><path d="M10.6 5.1A10.4 10.4 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.1"/><path d="M6.5 6.6C3.7 8.4 2 12 2 12s3.5 7 10 7a10 10 0 0 0 4.2-.9"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>
</button>
</#macro>

<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false wide=false>
<!DOCTYPE html>
<html lang="<#if locale??>${locale.currentLanguageTag}<#else>it</#if>"<#if locale?? && locale.rtl> dir="rtl"</#if>>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="robots" content="noindex, nofollow">
    <meta name="color-scheme" content="light dark">
    <meta name="theme-color" content="#F1E6CC">
    <title>Diario GDR</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.svg" type="image/svg+xml">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet">
        </#list>
    </#if>
    <script src="${url.resourcesPath}/js/theme.js"></script>
    <script src="${url.resourcesPath}/js/gdr.js" defer></script>
    <#if authenticationSession??>
        <script type="module">
            import("${url.resourcesPath}/js/authChecker.js")
                .then((checker) => {
                    if (typeof checker.checkCookiesAndSetTimer === "function") {
                        checker.checkCookiesAndSetTimer(
                            "${authenticationSession.authSessionId}",
                            "${authenticationSession.tabId}",
                            "${url.ssoLoginInOtherTabsUrl?no_esc}"
                        );
                    }
                })
                .catch(() => undefined);
        </script>
    </#if>
</head>
<body class="${bodyClass} font-body text-[20px] leading-[1.55] text-ink surface-page" data-page-id="login-${(pageId)!''}">
<div class="flex min-h-dvh flex-col surface-hero">
    <header class="mx-auto flex w-full max-w-[1200px] items-center justify-between gap-x-4 py-[22px] pr-[max(clamp(16px,4vw,48px),env(safe-area-inset-right))] pl-[max(clamp(16px,4vw,48px),env(safe-area-inset-left))]">
        <a href="${(client.baseUrl)!'#'}" class="inline-flex min-h-[44px] items-center gap-2.5 text-ink no-underline">
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" class="text-orn"><path d="M20 4c-6 0-11 4-13 10l-3 6 6-3c6-2 10-7 10-13z"/><path d="M7 17l7-7"/></svg>
            <span class="font-display text-[clamp(24px,7vw,32px)] leading-none font-bold">Diario GDR</span>
        </a>
        <#if realm.internationalizationEnabled && locale?? && locale.supported?size gt 1>
            <nav aria-label="${msg("languages")}" class="flex items-center gap-x-4 font-label text-[13px] font-medium tracking-[.14em] uppercase">
                <#list locale.supported as l>
                    <a href="${l.url}" class="inline-flex min-h-[44px] items-center text-nav-ink no-underline hover:opacity-80"<#if l.languageTag == locale.currentLanguageTag> aria-current="true"</#if>>${l.label}</a>
                </#list>
            </nav>
        </#if>
    </header>

    <main class="mx-auto flex w-full <#if wide>max-w-[760px]<#else>max-w-[560px]</#if> flex-1 flex-col pr-[max(clamp(16px,4vw,48px),env(safe-area-inset-right))] pb-[max(48px,env(safe-area-inset-bottom))] pl-[max(clamp(16px,4vw,48px),env(safe-area-inset-left))]">
        <div class="min-w-0 rounded-[6px] border border-(color:--card-border) px-[clamp(20px,6vw,40px)] pt-9 pb-10 shadow-(--card-shadow) outline-1 -outline-offset-8 outline-(color:--card-outline) surface-card">
            <div aria-hidden="true" class="mx-auto flex size-[72px] items-center justify-center rounded-full shadow-(--seal-shadow) surface-seal">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="text-seal-ink"><path d="M20 4c-6 0-11 4-13 10l-3 6 6-3c6-2 10-7 10-13z"/><path d="M7 17l7-7"/></svg>
            </div>

            <h1 id="kc-page-title" class="mx-0 mt-6 mb-0 text-center font-display text-[31px] leading-[1.08] font-bold text-h3"><#nested "header"></h1>
            <#if displayRequiredFields>
                <p class="mx-0 mt-3 mb-0 text-center text-base text-soft"><span class="text-danger">*</span> ${msg("requiredFields")}</p>
            </#if>

            <div aria-hidden="true" class="my-6 flex items-center gap-4">
                <div class="h-px flex-1 bg-(--rule)"></div>
                <div class="size-2.5 rotate-45 bg-orn"></div>
                <div class="h-px flex-1 bg-(--rule)"></div>
            </div>

            <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
                <#nested "show-username">
            <#else>
                <div id="kc-username" class="mb-5 flex flex-wrap items-center justify-between gap-x-4">
                    <span id="kc-attempted-username" class="font-body text-[20px] text-ink">${auth.attemptedUsername}</span>
                    <#if !isAppInitiatedAction??>
                        <a id="reset-login" href="${url.loginRestartFlowUrl}" class="${properties.kcLinkClass!}">${msg("restartLoginTooltip")}</a>
                    </#if>
                </div>
            </#if>

            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <#assign alertColor = (message.type == 'error')?then('border-(color:--danger)', 'border-(color:--orn)')>
                <div role="<#if message.type == 'error'>alert<#else>status</#if>" class="mb-5 rounded-[4px] border border-l-4 bg-(--page) px-4 py-3 text-[18px] leading-snug text-page-ink ${alertColor}">
                    <span>${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
                <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" class="mt-5">
                    <input type="hidden" name="tryAnotherWay" value="on">
                    <button type="submit" id="try-another-way" class="${properties.kcLinkClass!}">${msg("doTryAnotherWay")}</button>
                </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
                <div id="kc-info" class="mt-6 border-t border-(color:--rule) pt-5 text-center text-base text-soft">
                    <#nested "info">
                </div>
            </#if>
        </div>
    </main>
</div>
</body>
</html>
</#macro>
