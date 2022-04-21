package no.nav.saas.proxy.service

class SaasProxyService {

    /*
    val environment = Environment()
    val kafkaProducerDone = KafkaProducerWrapper(System.getenv("KAFKA_TOPIC_DONE"), KafkaProducer<NokkelInput, DoneInput>(
        KafkaConfig.producerProps(environment, Eventtype.DONE)))
    val kafkaProducerInnboks = KafkaProducerWrapper(System.getenv("KAFKA_TOPIC_INNBOKS"), KafkaProducer<NokkelInput, InnboksInput>(
        KafkaConfig.producerProps(environment, Eventtype.INNBOKS)))

    fun sendInnboks(eventId: String, grupperingsId: String, fodselsnummer: String, innboks: InnboksInput) {
        kafkaProducerInnboks.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), innboks)
    }

    fun sendDone(eventId: String, grupperingsId: String, fodselsnummer: String, done: DoneInput) {
        kafkaProducerDone.sendEvent(createKey(eventId, grupperingsId, fodselsnummer), done)
    }

    fun createKey(eventId: String, grupperingsId: String, fodselsnummer: String): NokkelInput {
        return NokkelInputBuilder()
            .withEventId(eventId)
            .withFodselsnummer(fodselsnummer)
            .withGrupperingsId(grupperingsId)
            .withNamespace(environment.namespace)
            .withAppnavn(environment.appnavn)
            .build()
    }

     */
}
