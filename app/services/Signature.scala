/**
  * This file is part of agora-board.
  * Copyright (C) 2016  Agora Voting SL <agora@agoravoting.com>
  *
  * agora-board is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License.
  *
  * agora-board is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.
  *
  * You should have received a copy of the GNU Affero General Public License
  * along with agora-board.  If not, see <http://www.gnu.org/licenses/>.
  **/

package services

import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModSafePrime
import ch.bfh.unicrypt.crypto.schemes.signature.classes.SchnorrSignatureScheme
import ch.bfh.unicrypt.helper.math.Alphabet
import ch.bfh.unicrypt.math.algebra.concatenative.classes.StringMonoid
import ch.bfh.unicrypt.math.algebra.general.classes.Pair
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModElement
import ch.bfh.unicrypt.math.algebra.multiplicative.classes.GStarModPrime
import java.security.KeyPair
import java.security.interfaces.DSAPrivateKey
import java.security.interfaces.DSAPublicKey
import java.security.spec.DSAPublicKeySpec
import java.security.KeyFactory
import java.nio.charset.StandardCharsets
import ch.bfh.unicrypt.math.algebra.dualistic.classes.ZModPrime
import java.math.BigInteger
import scala.util.{Try, Success, Failure}
import models._

//todo: throw away all the file, replace by the scrypto's Ed25519

case class DSASignature(signerPK: DSAPublicKey, signaturePK: GStarModElement, signature: Pair)
  extends BoardJSONFormatter {

  def test(el: GStarModElement, a: GStarModSafePrime) {
    el.convertToString()
    a.getModulus().toString()
    GStarModSafePrime.getInstance(new BigInteger(a.getModulus().toString()))
  }

  def toSignatureString(): SignatureString = {
    new models.SignatureString(
      DSAPublicKeyString(
        signerPK.getY().toString(),
        signerPK.getParams().getP().toString(),
        signerPK.getParams().getQ().toString(),
        signerPK.getParams().getG().toString()
      ),
      signaturePK.convertToString(),
      SignatureElements(
        signature.getFirst().convertToBigInteger().toString(),
        signature.getSecond().convertToBigInteger().toString(),
        signature.getFirst().getSet().getZModOrder().getModulus().toString()))
  }

  def verify(hash: Hash): Boolean = {
    // group
    val g_q = GStarModPrime.getInstance(
      signerPK.getParams().getP(),
      signerPK.getParams().getQ())
    val g = g_q.getElement(signerPK.getParams().getG())
    // Schnorr signature scheme
    val schnorr = SchnorrSignatureScheme.getInstance(
      StringMonoid.getInstance(Alphabet.BASE64), g)
    // message in Element format
    val messageElement =
      schnorr.getMessageSpace()
        .getElementFrom(new BigInteger(hash.toString().getBytes(StandardCharsets.UTF_8)))
    // verify
    schnorr.verify(signaturePK, messageElement, signature).isTrue()
  }
}

object SchnorrSigningDevice {
  //todo: message instead of hash
  def signString(keypair: KeyPair, hash: Hash): DSASignature = {
    val dsaPrivateKey = keypair.getPrivate().asInstanceOf[DSAPrivateKey]
    // group
    val g_q = GStarModPrime.getInstance(
      dsaPrivateKey.getParams().getP(),
      dsaPrivateKey.getParams().getQ())
    val g = g_q.getElement(dsaPrivateKey.getParams().getG())
    // Schnorr signature scheme
    val schnorr = SchnorrSignatureScheme.getInstance(
      StringMonoid.getInstance(Alphabet.BASE64), g)
    // message in Element format
    val foo = new BigInteger(hash.toString().getBytes(StandardCharsets.UTF_8))
    val message =
      schnorr.getMessageSpace()
        .getElementFrom(foo)
    // signature keys
    val keyPair = schnorr.getKeyPairGenerator().generateKeyPair()
    val privateKey = keyPair.getFirst()
    // signature
    val signature: Pair = schnorr.sign(privateKey, message)

    new DSASignature(
      keypair.getPublic().asInstanceOf[DSAPublicKey],
      keyPair.getSecond().asInstanceOf[GStarModElement],
      signature)
  }
}

object DSASignature {

  def fromSignatureString(sig: SignatureString): Option[DSASignature] = {
    Try {
      // signer PK reconstruction
      val dsaPublicKeySpec =
        new DSAPublicKeySpec(
          new BigInteger(sig.signerPK.y),
          new BigInteger(sig.signerPK.p),
          new BigInteger(sig.signerPK.q),
          new BigInteger(sig.signerPK.g))
      val keyFactory = KeyFactory.getInstance("DSA")
      val dsaPublicKey =
        keyFactory.generatePublic(dsaPublicKeySpec)
          .asInstanceOf[DSAPublicKey]

      // group
      val g_q = GStarModPrime.getInstance(
        dsaPublicKey.getParams().getP(),
        dsaPublicKey.getParams().getQ())
      val g2 = g_q.getElement(dsaPublicKey.getParams().getG())

      // signature PK reconstruction: GStarModElement
      val publicKey = g_q.getElementFrom(sig.signaturePK)
      // signature reconstruction: Pair[ZModElement]
      // first reconstruct the group
      val zmod = ZModPrime.getInstance(new BigInteger(sig.signature.zmod))
      // then the elements (they share the same zmod group)
      val zFirst = zmod.getElement(new BigInteger(sig.signature.first))
      val zSecond = zmod.getElement(new BigInteger(sig.signature.second))
      // the signature is the pair of elements
      val signaturePair = Pair.getInstance(zFirst, zSecond)
      Some(new DSASignature(dsaPublicKey, publicKey, signaturePair))
    } match {
      case Success(some) => some
      case Failure(error) => None
    }
  }
}