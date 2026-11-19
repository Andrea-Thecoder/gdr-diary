<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("lhEmailTestEyebrow") title=msg("lhEmailTestTitle") preheader=msg("lhEmailTestText")>
<@layout.text>${msg("lhEmailTestText")}</@layout.text>
</@layout.emailLayout>
