package adra.ifsp.edu.br.api.config;

import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

@Configuration
public class TransacaoConfig {

    @Bean
    public TransactionManagerCustomizer<AbstractPlatformTransactionManager> semConexaoPresaForaDeTransacao() {
        return gerenciador -> gerenciador.setTransactionSynchronization(
                AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
    }
}
