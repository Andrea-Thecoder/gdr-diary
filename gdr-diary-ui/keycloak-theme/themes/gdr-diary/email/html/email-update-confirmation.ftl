<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("gdrEmailUpdateEyebrow") title=msg("gdrEmailUpdateTitle") preheader=msg("gdrEmailUpdateText", newEmail)>
<@layout.text>${msg("gdrEmailUpdateText", newEmail)}</@layout.text>
<@layout.action href=link label=msg("gdrEmailUpdateButton") />
<@layout.note>${msg("gdrEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("gdrEmailUpdateIgnore")}</@layout.note>
</@layout.emailLayout>
