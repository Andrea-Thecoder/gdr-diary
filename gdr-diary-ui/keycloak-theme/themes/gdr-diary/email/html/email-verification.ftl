<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("gdrEmailVerificationEyebrow") title=msg("gdrEmailVerificationTitle") preheader=msg("gdrEmailVerificationText")>
<@layout.text>${msg("gdrEmailVerificationText")}</@layout.text>
<@layout.action href=link label=msg("gdrEmailVerificationButton") />
<@layout.note>${msg("gdrEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("gdrEmailVerificationIgnore")}</@layout.note>
</@layout.emailLayout>
