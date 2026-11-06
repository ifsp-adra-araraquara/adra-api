package adra.ifsp.edu.br.api.infra.armazenamento;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;

import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;

public interface ArmazenamentoArquivos {

    ProvedorArmazenamento provedor();

    void salvar(String chave, byte[] conteudo, String mimeType);

    URI urlTemporaria(String chave, Duration validade);

    Map<String, URI> urlsTemporarias(Collection<String> chaves, Duration validade);

    void remover(String chave);
}