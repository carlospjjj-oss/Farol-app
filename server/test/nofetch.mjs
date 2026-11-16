// Testes do servidor sem internet: qualquer fetch que o teste não simular responde "falhou".
globalThis.fetch = async () => ({ ok: false, status: 599, json: async () => ({}), text: async () => "" });
