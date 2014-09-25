#ifndef _AES_H
#define _AES_H

#ifndef uint8
#define uint8  unsigned char
#endif

#ifndef uint32
#define uint32 unsigned long int
#endif

typedef struct
{
    uint32 erk[64];     /* encryption round keys */
    uint32 drk[64];     /* decryption round keys */
    int nr;             /* number of rounds */
}
aes_context;

int  aes_set_key( aes_context *ctx, const uint8 *key, int nbits );
void aes_encrypt( const aes_context *ctx, const uint8 input[16], uint8 output[16] );
void aes_decrypt( const aes_context *ctx, const uint8 input[16], uint8 output[16] );
void aes_cbc_encrypt( const aes_context *ctx, const uint8 *input, uint8 *output,
                      uint32 len, const uint8 *iv );
void aes_cbc_decrypt( const aes_context *ctx, const uint8 *input, uint8 *output,
                      uint32 len, const uint8 *iv );

/* run key generation in reverse, from the given round back to round 0. */
void aes_reverse_keygen(int round, uint8 *key);

#endif /* aes.h */
