package adra.ifsp.edu.br.api.infra.armazenamento;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import adra.ifsp.edu.br.api.domain.enums.ProvedorArmazenamento;

public interface ArmazenamentoArquivos {

    ProvedorArmazenamento provedor();

    void salvar(String chave, byte[] conteudo, String mimeType);

    URI urlTemporaria(String chave, Duration validade);

    Map<String, URI> urlsTemporarias(Collection<String> chaves, Duration validade);

    void remover(String chave);

    /** Objeto guardado no bucket (para achar arquivos sem dono). */
    record ObjetoArmazenado(String chave, Instant criadoEm, long tamanhoBytes) {
    }

    /** Todos os objetos do bucket. Usado só na varredura de órfãos, feita à mão pelo Coordenador. */
    List<ObjetoArmazenado> listarTodos();
}