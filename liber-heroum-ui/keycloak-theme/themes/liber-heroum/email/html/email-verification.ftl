<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("lhEmailVerificationEyebrow") title=msg("lhEmailVerificationTitle") preheader=msg("lhEmailVerificationText")>
<@layout.text>${msg("lhEmailVerificationText")}</@layout.text>
<@layout.action href=link label=msg("lhEmailVerificationButton") />
<@layout.note>${msg("lhEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("lhEmailVerificationIgnore")}</@layout.note>
</@layout.emailLayout>
