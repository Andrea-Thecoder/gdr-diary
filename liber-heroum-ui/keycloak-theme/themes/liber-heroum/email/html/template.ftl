<#ftl output_format="HTML">
<#macro emailLayout eyebrow title preheader="">
<!DOCTYPE html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>${title}</title>
<style>
:root { color-scheme: light dark; supported-color-schemes: light dark; }
a { color: inherit; }
@media (max-width: 620px) {
  .lh-card { padding: 32px 22px 30px 22px !important; }
  .lh-title { font-size: 28px !important; }
  .lh-outer { padding: 20px 12px !important; }
}
@media (prefers-color-scheme: dark) {
  .lh-page { background: #0b0314 !important; }
  .lh-card { background: #12051f !important; border-color: #6e5c35 !important; }
  .lh-ink { color: #f4eaff !important; }
  .lh-soft { color: #cdbdf2 !important; }
  .lh-label { color: #e2be6a !important; }
  .lh-rule { border-color: #5d4f2e !important; }
  .lh-orn { color: #e2be6a !important; }
  .lh-seal { background: #5b1a99 !important; color: #f8e3a0 !important; border-color: #e2be6a !important; }
  .lh-btn { background: #5b1a99 !important; border-color: #e2be6a !important; color: #fff8e6 !important; }
  .lh-btn-text { color: #fff8e6 !important; }
  .lh-link { color: #e2be6a !important; }
}
</style>
</head>
<body class="lh-page" style="margin:0;padding:0;background:#f1e6cc;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;font-size:1px;line-height:1px;">${preheader}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="lh-page" style="background:#f1e6cc;">
<tr>
<td align="center" class="lh-outer" style="padding:36px 16px;">
<table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:560px;">
<tr>
<td class="lh-card" align="center" style="padding:40px 40px 36px 40px;background:#faf3e1;border:1px solid #d9c89c;border-radius:6px;font-family:'Palatino Linotype','Book Antiqua',Palatino,Georgia,serif;">
<table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center">
<tr>
<td class="lh-seal" align="center" width="68" height="68" style="width:68px;height:68px;box-sizing:border-box;border-radius:34px;background:#8b2b22;border:3px solid #8b2b22;color:#faf3e1;font-family:'Palatino Linotype','Book Antiqua',Palatino,Georgia,serif;font-size:24px;font-weight:bold;letter-spacing:1px;line-height:62px;text-align:center;">${msg("lhEmailMonogram")}</td>
</tr>
</table>
<p class="lh-label" style="margin:24px 0 0 0;font-size:12px;line-height:1.4;font-weight:bold;letter-spacing:2.4px;text-transform:uppercase;color:#6f5220;">${eyebrow}</p>
<h1 class="lh-ink lh-title" style="margin:8px 0 0 0;font-size:34px;line-height:1.12;font-weight:bold;color:#2a1f16;">${title}</h1>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:26px 0 24px 0;">
<tr>
<td class="lh-rule" style="border-top:1px solid #cdb98a;font-size:0;line-height:0;" height="1">&nbsp;</td>
<td class="lh-orn" width="32" align="center" style="width:32px;font-size:12px;line-height:12px;color:#8b2b22;">&#9670;</td>
<td class="lh-rule" style="border-top:1px solid #cdb98a;font-size:0;line-height:0;" height="1">&nbsp;</td>
</tr>
</table>
<#nested>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="margin:30px 0 22px 0;">
<tr>
<td class="lh-rule" style="border-top:1px solid #cdb98a;font-size:0;line-height:0;" height="1">&nbsp;</td>
<td class="lh-orn" width="32" align="center" style="width:32px;font-size:12px;line-height:12px;color:#8b2b22;">&#9670;</td>
<td class="lh-rule" style="border-top:1px solid #cdb98a;font-size:0;line-height:0;" height="1">&nbsp;</td>
</tr>
</table>
<p class="lh-ink" style="margin:0;font-size:20px;line-height:1.2;font-weight:bold;color:#2a1f16;">${msg("lhEmailBrand")}</p>
<p class="lh-label" style="margin:6px 0 0 0;font-size:11px;line-height:1.5;letter-spacing:2px;text-transform:uppercase;color:#6f5220;">${msg("lhEmailTagline")}</p>
</td>
</tr>
</table>
</td>
</tr>
</table>
</body>
</html>
</#macro>

<#macro text>
<p class="lh-soft" style="margin:0 0 20px 0;font-size:18px;line-height:1.6;color:#5a4a3a;text-align:center;"><#nested></p>
</#macro>

<#macro action href label>
<table role="presentation" cellpadding="0" cellspacing="0" border="0" align="center" style="margin:8px auto 22px auto;">
<tr>
<td class="lh-btn" align="center" style="border-radius:4px;background:#8b2b22;border:1px solid #8b2b22;">
<a href="${href}" class="lh-btn-text" style="display:inline-block;padding:17px 34px;font-family:'Palatino Linotype','Book Antiqua',Palatino,Georgia,serif;font-size:14px;line-height:1.2;font-weight:bold;letter-spacing:2px;text-transform:uppercase;text-decoration:none;color:#fbf4e4;">${label}</a>
</td>
</tr>
</table>
</#macro>

<#macro note>
<p class="lh-soft" style="margin:0 0 14px 0;font-size:16px;line-height:1.55;color:#5a4a3a;text-align:center;"><#nested></p>
</#macro>
