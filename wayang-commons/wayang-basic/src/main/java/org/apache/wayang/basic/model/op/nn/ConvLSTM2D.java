/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.wayang.basic.model.op.nn;

import org.apache.wayang.basic.model.op.Op;

public class ConvLSTM2D extends Op {
    private final int inputDim;
    private final int hiddenDim;
    private final int[] kernelSize;
    private final int[] stride;
    private final boolean bias;
    private final String output; // output keys: output, hidden, cell

    public ConvLSTM2D(int inputDim, int hiddenDim, int[] kernelSize, int[] stride, boolean bias, String output) {
        this(inputDim, hiddenDim, kernelSize, stride, bias, output, null, DType.FLOAT32);
    }

    public ConvLSTM2D(int inputDim, int hiddenDim, int[] kernelSize, int[] stride, boolean bias, String output, DType dType) {
        this(inputDim, hiddenDim, kernelSize, stride, bias, output, null, dType);
    }

    public ConvLSTM2D(int inputDim, int hiddenDim, int[] kernelSize, int[] stride, boolean bias, String output, String name) {
        this(inputDim, hiddenDim, kernelSize, stride, bias, output, name, DType.FLOAT32);
    }

    public ConvLSTM2D(int inputDim, int hiddenDim, int[] kernelSize, int[] stride, boolean bias, String output, String name, DType dType) {
        super(name, dType);
        this.inputDim = inputDim;
        this.hiddenDim = hiddenDim;
        this.kernelSize = kernelSize;
        this.stride = stride;
        this.bias = bias;
        this.output = output;
    }

    public boolean getBias() {
        return bias;
    }

    public int[] getStride() {
        return stride;
    }

    public int[] getKernelSize() {
        return kernelSize;
    }

    public int getHiddenDim() {
        return hiddenDim;
    }

    public int getInputDim() {
        return inputDim;
    }

    public String getOutput() {
        return output;
    }

    @Override
    public int inputsRequired() {
        return 1;
    }
}
