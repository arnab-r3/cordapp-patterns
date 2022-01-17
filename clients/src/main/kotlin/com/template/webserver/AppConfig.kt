package com.template.webserver

import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
open class AppConfig : WebMvcConfigurer {

    @Value("\${partyA.rpcUrl}")
    lateinit var partyAHostAndPort: String

    @Value("\${partyB.rpcUrl}")
    lateinit var partyBHostAndPort: String

    @Value("\${partyC.rpcUrl}")
    lateinit var partyCHostAndPort: String

    @Value("\${partyD.rpcUrl}")
    lateinit var partyDHostAndPort: String

    @Value("\${rpc.username}")
    lateinit var rpcUserName: String


    @Value("\${rpc.password}")
    lateinit var rpcPassword: String

    @Bean(destroyMethod = "")
    open fun partyAProxy(): CordaRPCOps {
        val partyAClient = CordaRPCClient(NetworkHostAndPort.parse(partyAHostAndPort))
        return partyAClient.start(rpcUserName, rpcPassword).proxy
    }

    @Bean(destroyMethod = "")
    open fun partyBProxy(): CordaRPCOps {
        val partyAClient = CordaRPCClient(NetworkHostAndPort.parse(partyBHostAndPort))
        return partyAClient.start(rpcUserName, rpcPassword).proxy
    }


    @Bean(destroyMethod = "")
    open fun partyCProxy(): CordaRPCOps {
        val partyAClient = CordaRPCClient(NetworkHostAndPort.parse(partyCHostAndPort))
        return partyAClient.start(rpcUserName, rpcPassword).proxy
    }

    @Bean(destroyMethod = "")
    open fun partyDProxy(): CordaRPCOps {
        val partyDClient = CordaRPCClient(NetworkHostAndPort.parse(partyDHostAndPort))
        return partyDClient.start(rpcUserName, rpcPassword).proxy
    }


    /**
     * Corda Jackson Support, to convert corda objects to json
     */
    @Bean
    open fun mappingJackson2HttpMessageConverter(@Autowired partyAProxy: CordaRPCOps): MappingJackson2HttpMessageConverter {
        val mapper = JacksonSupport.createDefaultMapper(rpc = partyAProxy, fuzzyIdentityMatch = true)
        mapper.registerModule(KotlinModule())
        val converter = MappingJackson2HttpMessageConverter()
        converter.objectMapper = mapper
        return converter
    }

}