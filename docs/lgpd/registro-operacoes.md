# Registro das Operações de Tratamento — Farol (LGPD, art. 37)

**Controlador:** responsável pelo Farol (ver `CONTROLADOR_NOME` no servidor) · **Canal do titular:** `CONTATO_PRIVACIDADE` / Ajustes → Privacidade
**Versão:** 2026-11-13 · **Próxima revisão:** 2027-05

| # | Operação | Titulares | Dados | Finalidade | Base legal | Onde | Operadores | Retenção | Medidas |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Cadastro e autenticação | Usuários (motoristas/entregadores) | Nome, e-mail, hash PBKDF2 da senha | Conta e acesso | Art. 7º, V | D1 (Cloudflare) | Cloudflare | Enquanto a conta existir | Hash com sal; bloqueio após 8 erros; HTTPS |
| 2 | Sessões | Usuários | Hash do token, código do aparelho, datas | Manter login | Art. 7º, V | D1 + Keystore no aparelho | Cloudflare | 180 dias sem uso | Token só como hash no servidor; cifrado no aparelho |
| 3 | Diário (turnos, ganhos, abastecimentos) | Usuários | Km, horários, pausas, ganhos por app, litros, valores | Sincronizar e restaurar; painel | Art. 7º, V | Aparelho (SQLite) + D1 | Cloudflare | Enquanto a conta existir | Escopo por conta; exclusão imediata a pedido |
| 4 | Números das ofertas | Usuários | App, preço, km, tempo, cor, data e hora | Estatísticas do painel | Art. 7º, V | Aparelho + D1 | Cloudflare | Enquanto a conta existir | Sem localização; sem texto sem consentimento |
| 5 | Textos das telas (opcional) | Usuários e, indiretamente, terceiros citados em telas (nomes de lojas e bairros) | Texto das ofertas e telas seguintes, mascarado no aparelho | Melhorar o leitor; testes anonimizados | Art. 7º, I (consentimento) | D1 + repositório privado GitHub | Cloudflare, GitHub | Texto: 90 dias; cópia: até revogação ou exclusão | Opt-in separado; mascaramento; revogação apaga; anonimização antes de virar teste |
| 6 | Segurança de login | Quem tenta entrar | E-mail tentado, IP | Impedir força bruta | Art. 7º, IX | D1 | Cloudflare | 24 h | Retenção curta; uso exclusivo para bloqueio |
| 7 | Diagnóstico de travamentos | Usuários | Versão, modelo, Android, tela, pilha do erro (mascarada) | Corrigir falhas | Art. 7º, IX | D1 | Cloudflare | 180 dias | Sem conteúdo pessoal; acesso só do admin |
| 8 | Atualização do app | Usuários | IP e user-agent em download | Distribuir versões | Art. 7º, V | GitHub Releases | GitHub | Política do GitHub | Verificação SHA-256 e certificado no app |
| 9 | OCR no aparelho | Usuários | Imagem da tela (em memória) | Ler oferta quando a acessibilidade falha | Art. 7º, V | Somente aparelho | Google (métricas técnicas do ML Kit, sem imagem) | Descartada na hora | Processamento local; só apps monitorados |
| 10 | GPS do turno | Usuários | Pontos de localização | Sugerir km rodados | Art. 7º, V | Somente aparelho | — | No aparelho | Não enviado ao servidor; só com turno ativo |

**Legítimo interesse (operações 6 e 7) — teste de balanceamento resumido:** a finalidade é legítima (segurança e correção de falhas); os dados são os mínimos necessários (IP por 24 h; erro técnico sem conteúdo); é algo que o titular espera de um app com conta; o impacto é baixo e há direito de oposição pelo canal do titular. Conclusão: adequado.
