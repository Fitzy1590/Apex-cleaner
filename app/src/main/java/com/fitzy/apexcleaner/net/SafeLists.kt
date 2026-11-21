package com.fitzy.apexcleaner.net

/** Domains that are never blocked or inspected: banking, payments, government, and phone essentials. */
object SafeLists {
    /** Banking, payment and government sites. Parent domains cover their sub-domains. */
    private const val BANKING = """
chase.com jpmorgan.com bankofamerica.com wellsfargo.com citi.com citibank.com usbank.com pnc.com capitalone.com
truist.com tdbank.com td.com schwab.com fidelity.com vanguard.com etrade.com ally.com discover.com americanexpress.com
amex.com synchrony.com navyfederal.org usaa.com regions.com fifththird.com huntington.com keybank.com citizensbank.com
mtb.com santander.com santanderbank.com hsbc.com hsbc.co.uk barclays.co.uk barclays.com lloydsbank.com lloydsbank.co.uk
natwest.com rbs.co.uk halifax.co.uk nationwide.co.uk tsb.co.uk metrobankonline.co.uk monzo.com starlingbank.com
revolut.com wise.com n26.com chime.com sofi.com varomoney.com current.com
rbc.com royalbank.com td.ca cibc.com bmo.com scotiabank.com tangerine.ca desjardins.com nbc.ca
commbank.com.au anz.com.au westpac.com.au nab.com.au ing.com.au macquarie.com.au bendigobank.com.au
bnz.co.nz asb.co.nz anz.co.nz kiwibank.co.nz
deutsche-bank.de commerzbank.de sparkasse.de ing.de comdirect.de dkb.de postbank.de
bnpparibas.com bnpparibas.fr societegenerale.fr creditagricole.fr labanquepostale.fr lcl.fr boursorama.com
ubs.com credit-suisse.com bbva.com bbva.es caixabank.es bancosantander.es intesasanpaolo.com unicredit.it
abnamro.nl rabobank.nl ing.nl bunq.com nordea.com danskebank.com seb.se swedbank.se handelsbanken.se
dbs.com ocbc.com uob.com.sg maybank.com cimb.com hdfcbank.com icicibank.com sbi.co.in axisbank.com kotak.com
paytm.com phonepe.com
itau.com.br bradesco.com.br santander.com.br nubank.com.br banco.bradesco bancodobrasil.com.br bancolombia.com
paypal.com paypalobjects.com stripe.com squareup.com cash.app venmo.com zellepay.com klarna.com afterpay.com affirm.com
adyen.com worldpay.com braintreegateway.com checkout.com razorpay.com payoneer.com skrill.com neteller.com
visa.com mastercard.com mastercard.us discover.com
wisetransfer.com westernunion.com moneygram.com remitly.com
coinbase.com kraken.com binance.com gemini.com robinhood.com
apple.com gsa.gov
irs.gov ssa.gov usa.gov login.gov id.me treasury.gov medicare.gov va.gov healthcare.gov
gov.uk hmrc.gov.uk nhs.uk
canada.ca servicecanada.gc.ca cra-arc.gc.ca
gov.au servicesaustralia.gov.au ato.gov.au mygov.au
europa.eu service-public.fr bund.de
gov.in incometax.gov.in uidai.gov.in
"""

    /** Push and system essentials: keeps alerts, alarms-over-network and connectivity checks instant. */
    private const val ESSENTIAL = """
mtalk.google.com fcm.googleapis.com android.googleapis.com android.clients.google.com
connectivitycheck.gstatic.com connectivitycheck.android.com clients3.google.com play.googleapis.com
time.android.com time.google.com ntp.org pool.ntp.org
gstatic.com googleapis.com accounts.google.com
push.apple.com apple.com
"""

    /** Small built-in starter blocklist of well-known ad, tracker and smart-TV telemetry names. */
    private const val STARTER_BLOCKS = """
doubleclick.net googlesyndication.com googleadservices.com adservice.google.com google-analytics.com
googletagmanager.com googletagservices.com pagead2.googlesyndication.com
adsrvr.org adnxs.com scorecardresearch.com taboola.com outbrain.com criteo.com criteo.net moatads.com
amazon-adsystem.com advertising.com rubiconproject.com pubmatic.com openx.net casalemedia.com
unityads.unity3d.com applovin.com ads.mopub.com mopub.com inmobi.com chartboost.com vungle.com ironsrc.com
ads.tiktok.com analytics.tiktok.com ads.twitter.com analytics.twitter.com ads.linkedin.com
ads.yahoo.com adtechus.com 2mdn.net serving-sys.com quantserve.com hotjar.com mixpanel.com
branch.io app.adjust.com adjust.com appsflyer.com kochava.com flurry.com crashlytics.com
samsungacr.com samsungads.com ad.samsungads.com log-config.samsungacr.com
lgsmartad.com ad.lgsmartad.com lgtvsdp.com tvinteractive.tv
"""

    private fun words(s: String) = s.split(Regex("\\s+")).filter { it.contains('.') }

    val bankingDomains: List<String> get() = words(BANKING).distinct()
    val essentialDomains: List<String> get() = words(ESSENTIAL).distinct()
    val starterBlocks: List<String> get() = words(STARTER_BLOCKS).distinct()

    /** Packages that bypass the tunnel completely, so their certificate pinning is never in play. */
    val bankingBypassPackages = setOf(
        "com.chase.sig.android", "com.infonow.bofa", "com.wf.wellsfargomobile", "com.citi.citimobile",
        "com.usaa.mobile.android.usaa", "com.konylabs.capitalone", "com.paypal.android.p2pmobile",
        "com.venmo", "com.squareup.cash", "com.google.android.apps.walletnfcrel", "com.samsung.android.spay",
        "com.revolut.revolut", "com.monzo.android", "com.starlingbank.android",
        "com.barclays.android.barclaysmobilebanking", "com.hsbc.hsbcuk", "uk.co.hsbc.hsbcukmobilebanking",
        "com.lloydsbank.mobilebanking", "com.rbs.mobile.android.natwest", "com.rbc.mobile.android",
        "com.td", "com.cibc.android.mobi", "com.bmo.mobile", "au.com.cba.mobile", "org.westpac.bank",
        "com.mobileloan.nab", "au.com.anz.mobile", "com.schwab.mobile", "com.fidelity.android",
        "com.coinbase.android", "com.robinhood.android", "com.wise.android", "com.n26.android",
    )
}
