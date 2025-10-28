package com.mycompany.app.services;

import java.util.List;

import com.mycompany.app.models.Crypto;

public interface CryptoService {
    List<Crypto> getTopCryptos();
}
